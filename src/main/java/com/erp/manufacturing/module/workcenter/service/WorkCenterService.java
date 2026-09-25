package com.erp.manufacturing.module.workcenter.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterCreateRequest;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterResponse;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterUpdateRequest;
import com.erp.manufacturing.module.workcenter.mapper.WorkCenterMapper;
import com.erp.manufacturing.module.workcenter.repository.WorkCenterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

/**
 * Work center master data (C2-6 Part A, {@code NEXT_PHASE_PLAN.md} §Phần A). Per-plant scoped —
 * {@code create}/{@code list} authorize against the {@code plantId} path segment directly;
 * {@code get}/{@code update}/{@code activate}/{@code deactivate} identify by aggregate and resolve
 * the plant through {@link WorkCenterPermissionGuard}, same shape as Routing.
 */
@Service
@RequiredArgsConstructor
public class WorkCenterService {

    private final WorkCenterRepository workCenterRepository;
    private final OrganizationLookupService organizationLookupService;
    private final WorkCalendarLookupService workCalendarLookupService;
    private final WorkCenterMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_CENTER_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.WORK_CENTER_CREATED, entityType = "WorkCenter", entityIdExpression = "workCenterId.toString()",
               plantId = "#result?.plantId()")
    public WorkCenterResponse create(UUID plantId, WorkCenterCreateRequest request) {
        Plant plant = organizationLookupService.getActivePlant(plantId);

        String code = normalizeCode(request.code());
        if (workCenterRepository.existsByPlantPlantIdAndCode(plantId, code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Work center code", code);
        }

        WorkCalendar workCalendar = request.workCalendarId() == null
                ? null
                : resolveWorkCalendarInPlant(request.workCalendarId(), plantId);

        WorkCenter workCenter = WorkCenter.builder()
                .plant(plant)
                .code(code)
                .name(requireText(request.name(), "Work center name"))
                .description(trimToNull(request.description()))
                .capacityUnitType(request.capacityUnitType())
                .capacityUnits(request.capacityUnits())
                .status(OrganizationStatus.ACTIVE)
                .workCalendar(workCalendar)
                .build();
        return mapper.toResponse(workCenterRepository.save(workCenter));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_CENTER_READ', 'PLANT', #plantId)")
    public PageResult<WorkCenterResponse> list(UUID plantId, OrganizationStatus status, Pageable pageable) {
        return PageResult.from(workCenterRepository.search(plantId, status, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workCenterPermissionGuard.hasWorkCenterAccess(authentication, 'PERM_WORK_CENTER_READ', #workCenterId)")
    public WorkCenterResponse get(UUID workCenterId) {
        return mapper.toResponse(findWorkCenter(workCenterId));
    }

    @Transactional
    @PreAuthorize("@workCenterPermissionGuard.hasWorkCenterAccess(authentication, 'PERM_WORK_CENTER_MANAGE', #workCenterId)")
    @Auditable(action = AuditAction.WORK_CENTER_UPDATED, entityType = "WorkCenter", entityIdExpression = "workCenterId.toString()",
               plantId = "#result?.plantId()")
    public WorkCenterResponse update(UUID workCenterId, WorkCenterUpdateRequest request) {
        WorkCenter workCenter = findWorkCenter(workCenterId);
        if (request.name() != null) {
            workCenter.setName(requireText(request.name(), "Work center name"));
        }
        if (request.description() != null) {
            workCenter.setDescription(trimToNull(request.description()));
        }
        if (request.capacityUnitType() != null) {
            workCenter.setCapacityUnitType(request.capacityUnitType());
        }
        if (request.capacityUnits() != null) {
            workCenter.setCapacityUnits(request.capacityUnits());
        }
        if (request.workCalendarId() != null) {
            workCenter.setWorkCalendar(resolveWorkCalendarInPlant(
                    request.workCalendarId(), workCenter.getPlant().getPlantId()));
        }
        return mapper.toResponse(workCenterRepository.save(workCenter));
    }

    @Transactional
    @PreAuthorize("@workCenterPermissionGuard.hasWorkCenterAccess(authentication, 'PERM_WORK_CENTER_MANAGE', #workCenterId)")
    @Auditable(action = AuditAction.WORK_CENTER_ACTIVATED, entityType = "WorkCenter", entityIdExpression = "workCenterId.toString()",
               plantId = "#result?.plantId()")
    public WorkCenterResponse activate(UUID workCenterId) {
        WorkCenter workCenter = findWorkCenter(workCenterId);
        workCenter.activate();
        return mapper.toResponse(workCenterRepository.save(workCenter));
    }

    /**
     * Also the handler behind {@code DELETE /work-centers/{workCenterId}} (rule C6): the plan's
     * decision #4 is that DELETE and this method are the same command, not two behaviors.
     *
     * <p>Deliberately does NOT check whether a {@code RoutingOperation} still references this work
     * center (NEXT_PHASE_PLAN.md C2-6 §Phần A #4): deactivating doesn't delete data, only flips
     * status, and {@code RoutingOperation} keeps its FK regardless — same as Warehouse {@code
     * INACTIVE} still holding {@code stock_balances} rows. Adding that check now would be scope
     * creep; it belongs to a future phase if FE asks for it.
     */
    @Transactional
    @PreAuthorize("@workCenterPermissionGuard.hasWorkCenterAccess(authentication, 'PERM_WORK_CENTER_MANAGE', #workCenterId)")
    @Auditable(action = AuditAction.WORK_CENTER_DEACTIVATED, entityType = "WorkCenter", entityIdExpression = "workCenterId.toString()",
               plantId = "#result?.plantId()")
    public WorkCenterResponse deactivate(UUID workCenterId) {
        WorkCenter workCenter = findWorkCenter(workCenterId);
        workCenter.deactivate();
        return mapper.toResponse(workCenterRepository.save(workCenter));
    }

    /**
     * Bất biến B_wc4 (module/workcenter/CLAUDE.md): a work center's calendar must belong to the
     * same plant as the work center itself — same shape as B_wc2's "same plant" requirement between
     * a routing operation and its work center. 422, not 409: invalid input, not a state conflict.
     */
    private WorkCalendar resolveWorkCalendarInPlant(UUID workCalendarId, UUID plantId) {
        WorkCalendar workCalendar = workCalendarLookupService.getActiveWorkCalendar(workCalendarId);
        if (!workCalendar.getPlant().getPlantId().equals(plantId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Work calendar " + workCalendarId + " does not belong to plant " + plantId);
        }
        return workCalendar;
    }

    private WorkCenter findWorkCenter(UUID workCenterId) {
        return workCenterRepository.findById(workCenterId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Work center", workCenterId));
    }

    private String normalizeCode(String value) {
        return requireText(value, "Work center code").toUpperCase(Locale.ROOT);
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
