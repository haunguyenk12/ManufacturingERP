package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.ShiftBreak;
import com.erp.manufacturing.module.shift.dto.ShiftBreakRequest;
import com.erp.manufacturing.module.shift.dto.ShiftCreateRequest;
import com.erp.manufacturing.module.shift.dto.ShiftResponse;
import com.erp.manufacturing.module.shift.dto.ShiftUpdateRequest;
import com.erp.manufacturing.module.shift.mapper.ShiftMapper;
import com.erp.manufacturing.module.shift.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Shift master data (C2-7 Part A, {@code NEXT_PHASE_PLAN.md} §Phần A). Per-plant scoped —
 * {@code create}/{@code list} authorize against the {@code plantId} path segment directly;
 * {@code get}/{@code update}/{@code activate}/{@code deactivate} identify by aggregate and resolve
 * the plant through {@link ShiftPermissionGuard} — same shape as {@code WorkCenterService}.
 */
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ShiftMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SHIFT_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.SHIFT_CREATED, entityType = "Shift", entityIdExpression = "shiftId.toString()")
    public ShiftResponse create(UUID plantId, ShiftCreateRequest request) {
        Plant plant = organizationLookupService.getActivePlant(plantId);

        String code = normalizeCode(request.code());
        if (shiftRepository.existsByPlantPlantIdAndCode(plantId, code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Shift code", code);
        }

        Shift shift = Shift.builder()
                .plant(plant)
                .code(code)
                .name(requireText(request.name(), "Shift name"))
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(OrganizationStatus.ACTIVE)
                .build();

        for (ShiftBreakRequest breakRequest : breaksOrEmpty(request.breaks())) {
            shift.getBreaks().add(buildBreak(shift, breakRequest));
        }
        ensureAllBreaksWithinWindow(shift.getStartTime(), shift.getEndTime(), shift.getBreaks());

        return mapper.toResponse(shiftRepository.save(shift));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SHIFT_READ', 'PLANT', #plantId)")
    public PageResult<ShiftResponse> list(UUID plantId, OrganizationStatus status, Pageable pageable) {
        return PageResult.from(shiftRepository.search(plantId, status, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@shiftPermissionGuard.hasShiftAccess(authentication, 'PERM_SHIFT_READ', #shiftId)")
    public ShiftResponse get(UUID shiftId) {
        return mapper.toResponse(findShift(shiftId));
    }

    @Transactional
    @PreAuthorize("@shiftPermissionGuard.hasShiftAccess(authentication, 'PERM_SHIFT_MANAGE', #shiftId)")
    @Auditable(action = AuditAction.SHIFT_UPDATED, entityType = "Shift", entityIdExpression = "shiftId.toString()")
    public ShiftResponse update(UUID shiftId, ShiftUpdateRequest request) {
        Shift shift = findShift(shiftId);
        if (request.name() != null) {
            shift.setName(requireText(request.name(), "Shift name"));
        }
        if (request.startTime() != null) {
            shift.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            shift.setEndTime(request.endTime());
        }
        if (request.breaks() != null) {
            shift.getBreaks().clear();
            for (ShiftBreakRequest breakRequest : request.breaks()) {
                shift.getBreaks().add(buildBreak(shift, breakRequest));
            }
        }
        // Re-validated even when `breaks` was not resent: startTime/endTime may have just changed,
        // and the invariant must hold against the shift's final window either way.
        ensureAllBreaksWithinWindow(shift.getStartTime(), shift.getEndTime(), shift.getBreaks());

        return mapper.toResponse(shiftRepository.save(shift));
    }

    @Transactional
    @PreAuthorize("@shiftPermissionGuard.hasShiftAccess(authentication, 'PERM_SHIFT_MANAGE', #shiftId)")
    @Auditable(action = AuditAction.SHIFT_ACTIVATED, entityType = "Shift", entityIdExpression = "shiftId.toString()")
    public ShiftResponse activate(UUID shiftId) {
        Shift shift = findShift(shiftId);
        shift.activate();
        return mapper.toResponse(shiftRepository.save(shift));
    }

    /**
     * Also the handler behind {@code DELETE /shifts/{shiftId}} (rule C6), and deliberately does NOT
     * check whether a {@code WorkCalendarWeeklyShift} still references this shift — same accepted
     * decision as {@code WorkCenterService.deactivate} (B_wc3 in module/workcenter/CLAUDE.md).
     */
    @Transactional
    @PreAuthorize("@shiftPermissionGuard.hasShiftAccess(authentication, 'PERM_SHIFT_MANAGE', #shiftId)")
    @Auditable(action = AuditAction.SHIFT_DEACTIVATED, entityType = "Shift", entityIdExpression = "shiftId.toString()")
    public ShiftResponse deactivate(UUID shiftId) {
        Shift shift = findShift(shiftId);
        shift.deactivate();
        return mapper.toResponse(shiftRepository.save(shift));
    }

    private ShiftBreak buildBreak(Shift shift, ShiftBreakRequest request) {
        return ShiftBreak.builder()
                .shift(shift)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();
    }

    /**
     * Bất biến (module/shift/CLAUDE.md): every break must lie within its parent shift's single
     * window, using the same circular (midnight-wrapping) containment as
     * {@link WorkingWindowCalculator} — 422, not 409: this is invalid <em>input</em>, not a
     * status-machine conflict (error-handling.md §5.3).
     */
    private void ensureAllBreaksWithinWindow(LocalTime shiftStart, LocalTime shiftEnd, List<ShiftBreak> breaks) {
        for (ShiftBreak shiftBreak : breaks) {
            if (!ShiftTimeWindow.containsInterval(shiftStart, shiftEnd, shiftBreak.getStartTime(), shiftBreak.getEndTime())) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Break " + shiftBreak.getStartTime() + "-" + shiftBreak.getEndTime()
                                + " is not within the shift window " + shiftStart + "-" + shiftEnd);
            }
        }
    }

    private List<ShiftBreakRequest> breaksOrEmpty(List<ShiftBreakRequest> breaks) {
        return breaks == null ? List.of() : breaks;
    }

    private Shift findShift(UUID shiftId) {
        return shiftRepository.findById(shiftId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Shift", shiftId));
    }

    private String normalizeCode(String value) {
        return requireText(value, "Shift code").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, fieldName + " is required");
        }
        return value.trim();
    }
}
