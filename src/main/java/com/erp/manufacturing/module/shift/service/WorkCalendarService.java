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
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.WorkCalendarException;
import com.erp.manufacturing.module.shift.domain.WorkCalendarWeeklyShift;
import com.erp.manufacturing.module.shift.dto.WorkCalendarCreateRequest;
import com.erp.manufacturing.module.shift.dto.WorkCalendarExceptionRequest;
import com.erp.manufacturing.module.shift.dto.WorkCalendarResponse;
import com.erp.manufacturing.module.shift.dto.WorkCalendarUpdateRequest;
import com.erp.manufacturing.module.shift.dto.WorkCalendarWeeklyShiftRequest;
import com.erp.manufacturing.module.shift.mapper.WorkCalendarMapper;
import com.erp.manufacturing.module.shift.repository.ShiftRepository;
import com.erp.manufacturing.module.shift.repository.WorkCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Work calendar master data — weekly shift pattern + one-off exceptions (C2-7 Part B,
 * {@code NEXT_PHASE_PLAN.md} §Phần B). Per-plant scoped, same shape as {@code ShiftService}.
 * Shift resolution stays inline (no separate lookup service): {@code Shift} and
 * {@code WorkCalendar} live in the same module, unlike Work Center which {@code routing} reaches
 * from outside.
 */
@Service
@RequiredArgsConstructor
public class WorkCalendarService {

    private final WorkCalendarRepository workCalendarRepository;
    private final ShiftRepository shiftRepository;
    private final OrganizationLookupService organizationLookupService;
    private final WorkCalendarMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_CALENDAR_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.WORK_CALENDAR_CREATED, entityType = "WorkCalendar", entityIdExpression = "workCalendarId.toString()",
               plantId = "#result?.plantId()")
    public WorkCalendarResponse create(UUID plantId, WorkCalendarCreateRequest request) {
        Plant plant = organizationLookupService.getActivePlant(plantId);

        String code = normalizeCode(request.code());
        if (workCalendarRepository.existsByPlantPlantIdAndCode(plantId, code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Work calendar code", code);
        }
        ensureEffectiveRangeValid(request.effectiveFrom(), request.effectiveTo());

        List<WorkCalendarWeeklyShiftRequest> weeklyShiftRequests = weeklyShiftsOrEmpty(request.weeklyShifts());
        List<WorkCalendarExceptionRequest> exceptionRequests = exceptionsOrEmpty(request.exceptions());
        ensureNoDuplicateExceptionDates(exceptionRequests);
        Map<UUID, Shift> shiftsById = resolveShiftsInPlant(weeklyShiftRequests, plant);

        WorkCalendar calendar = WorkCalendar.builder()
                .plant(plant)
                .code(code)
                .name(requireText(request.name(), "Work calendar name"))
                .effectiveFrom(request.effectiveFrom())
                .effectiveTo(request.effectiveTo())
                .status(OrganizationStatus.ACTIVE)
                .build();

        for (WorkCalendarWeeklyShiftRequest weeklyShiftRequest : weeklyShiftRequests) {
            calendar.getWeeklyShifts().add(buildWeeklyShift(calendar, weeklyShiftRequest, shiftsById));
        }
        for (WorkCalendarExceptionRequest exceptionRequest : exceptionRequests) {
            calendar.getExceptions().add(buildException(calendar, exceptionRequest));
        }
        return mapper.toResponse(workCalendarRepository.save(calendar));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_CALENDAR_READ', 'PLANT', #plantId)")
    public PageResult<WorkCalendarResponse> list(UUID plantId, OrganizationStatus status, Pageable pageable) {
        return PageResult.from(workCalendarRepository.search(plantId, status, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workCalendarPermissionGuard.hasWorkCalendarAccess(authentication, 'PERM_WORK_CALENDAR_READ', #workCalendarId)")
    public WorkCalendarResponse get(UUID workCalendarId) {
        return mapper.toResponse(findCalendarWithDetails(workCalendarId));
    }

    @Transactional
    @PreAuthorize("@workCalendarPermissionGuard.hasWorkCalendarAccess(authentication, 'PERM_WORK_CALENDAR_MANAGE', #workCalendarId)")
    @Auditable(action = AuditAction.WORK_CALENDAR_UPDATED, entityType = "WorkCalendar", entityIdExpression = "workCalendarId.toString()",
               plantId = "#result?.plantId()")
    public WorkCalendarResponse update(UUID workCalendarId, WorkCalendarUpdateRequest request) {
        WorkCalendar calendar = findCalendarWithDetails(workCalendarId);
        if (request.name() != null) {
            calendar.setName(requireText(request.name(), "Work calendar name"));
        }
        if (request.effectiveFrom() != null) {
            calendar.setEffectiveFrom(request.effectiveFrom());
        }
        if (request.effectiveTo() != null) {
            calendar.setEffectiveTo(request.effectiveTo());
        }
        ensureEffectiveRangeValid(calendar.getEffectiveFrom(), calendar.getEffectiveTo());

        if (request.weeklyShifts() != null) {
            Map<UUID, Shift> shiftsById = resolveShiftsInPlant(request.weeklyShifts(), calendar.getPlant());
            calendar.getWeeklyShifts().clear();
            for (WorkCalendarWeeklyShiftRequest weeklyShiftRequest : request.weeklyShifts()) {
                calendar.getWeeklyShifts().add(buildWeeklyShift(calendar, weeklyShiftRequest, shiftsById));
            }
        }
        if (request.exceptions() != null) {
            ensureNoDuplicateExceptionDates(request.exceptions());
            calendar.getExceptions().clear();
            for (WorkCalendarExceptionRequest exceptionRequest : request.exceptions()) {
                calendar.getExceptions().add(buildException(calendar, exceptionRequest));
            }
        }
        return mapper.toResponse(workCalendarRepository.save(calendar));
    }

    @Transactional
    @PreAuthorize("@workCalendarPermissionGuard.hasWorkCalendarAccess(authentication, 'PERM_WORK_CALENDAR_MANAGE', #workCalendarId)")
    @Auditable(action = AuditAction.WORK_CALENDAR_ACTIVATED, entityType = "WorkCalendar", entityIdExpression = "workCalendarId.toString()",
               plantId = "#result?.plantId()")
    public WorkCalendarResponse activate(UUID workCalendarId) {
        WorkCalendar calendar = findCalendar(workCalendarId);
        calendar.activate();
        return mapper.toResponse(workCalendarRepository.save(calendar));
    }

    /**
     * Also the handler behind {@code DELETE /work-calendars/{workCalendarId}} (rule C6). Deliberately
     * does NOT check whether a {@code WorkCenter} still references this calendar — same accepted
     * decision as {@code WorkCenterService.deactivate} (B_wc3).
     */
    @Transactional
    @PreAuthorize("@workCalendarPermissionGuard.hasWorkCalendarAccess(authentication, 'PERM_WORK_CALENDAR_MANAGE', #workCalendarId)")
    @Auditable(action = AuditAction.WORK_CALENDAR_DEACTIVATED, entityType = "WorkCalendar", entityIdExpression = "workCalendarId.toString()",
               plantId = "#result?.plantId()")
    public WorkCalendarResponse deactivate(UUID workCalendarId) {
        WorkCalendar calendar = findCalendar(workCalendarId);
        calendar.deactivate();
        return mapper.toResponse(workCalendarRepository.save(calendar));
    }

    private WorkCalendarWeeklyShift buildWeeklyShift(WorkCalendar calendar, WorkCalendarWeeklyShiftRequest request, Map<UUID, Shift> shiftsById) {
        return WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar)
                .weekday(request.weekday())
                .shift(shiftsById.get(request.shiftId()))
                .build();
    }

    private WorkCalendarException buildException(WorkCalendar calendar, WorkCalendarExceptionRequest request) {
        return WorkCalendarException.builder()
                .workCalendar(calendar)
                .exceptionDate(request.exceptionDate())
                .reason(trimToNull(request.reason()))
                .build();
    }

    /**
     * Resolves every {@code shiftId} up front and validates it belongs to {@code plant} BEFORE any
     * entity is built (rule C9) — the same-plant requirement is checked here rather than through a
     * separate lookup service because {@code Shift} and {@code WorkCalendar} live in the same
     * module (unlike Work Center's B_wc2, which crosses into {@code routing}).
     */
    private Map<UUID, Shift> resolveShiftsInPlant(List<WorkCalendarWeeklyShiftRequest> requests, Plant plant) {
        Set<UUID> shiftIds = requests.stream().map(WorkCalendarWeeklyShiftRequest::shiftId).collect(Collectors.toSet());
        if (shiftIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Shift> shiftsById = new HashMap<>();
        for (Shift shift : shiftRepository.findAllById(shiftIds)) {
            shiftsById.put(shift.getShiftId(), shift);
        }
        for (UUID shiftId : shiftIds) {
            Shift shift = shiftsById.get(shiftId);
            if (shift == null) {
                throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Shift", shiftId);
            }
            if (!shift.getPlant().getPlantId().equals(plant.getPlantId())) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                        "Shift " + shiftId + " does not belong to plant " + plant.getPlantId());
            }
        }
        return shiftsById;
    }

    private void ensureEffectiveRangeValid(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "effectiveTo must not be before effectiveFrom");
        }
    }

    private void ensureNoDuplicateExceptionDates(List<WorkCalendarExceptionRequest> exceptions) {
        Set<LocalDate> seen = new HashSet<>();
        for (WorkCalendarExceptionRequest exception : exceptions) {
            if (!seen.add(exception.exceptionDate())) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.BUSINESS_RULE_VIOLATION,
                        "Duplicate exception date: " + exception.exceptionDate());
            }
        }
    }

    private WorkCalendar findCalendar(UUID workCalendarId) {
        return workCalendarRepository.findById(workCalendarId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Work calendar", workCalendarId));
    }

    private WorkCalendar findCalendarWithDetails(UUID workCalendarId) {
        WorkCalendar calendar = workCalendarRepository.findWithWeeklyShiftsByWorkCalendarId(workCalendarId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Work calendar", workCalendarId));
        calendar.getExceptions().size(); // force-init the lazy bag left out of the @EntityGraph
        return calendar;
    }

    private List<WorkCalendarWeeklyShiftRequest> weeklyShiftsOrEmpty(List<WorkCalendarWeeklyShiftRequest> weeklyShifts) {
        return weeklyShifts == null ? List.of() : weeklyShifts;
    }

    private List<WorkCalendarExceptionRequest> exceptionsOrEmpty(List<WorkCalendarExceptionRequest> exceptions) {
        return exceptions == null ? List.of() : exceptions;
    }

    private String normalizeCode(String value) {
        return requireText(value, "Work calendar code").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
