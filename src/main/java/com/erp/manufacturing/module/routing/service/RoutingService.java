package com.erp.manufacturing.module.routing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.dto.RoutingCreateRequest;
import com.erp.manufacturing.module.routing.dto.RoutingOperationRequest;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import com.erp.manufacturing.module.routing.mapper.RoutingMapper;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoutingService {

    private final RoutingHeaderRepository routingHeaderRepository;
    private final ItemLookupService itemLookupService;
    private final WorkCenterLookupService workCenterLookupService;
    private final RoutingMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ROUTING_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.ROUTING_CREATED, entityType = "RoutingHeader", entityIdExpression = "routingId.toString()")
    public RoutingResponse create(UUID companyId, RoutingCreateRequest request) {
        Item item = itemLookupService.getActiveItem(request.itemId());
        ensureManufacturableItem(item);
        ensureItemBelongsToCompany(item, companyId);

        String code = normalize(request.code(), "Routing code");
        String version = normalize(request.version(), "Routing version");
        if (routingHeaderRepository.existsByCompanyCompanyIdAndCodeAndRoutingVersion(companyId, code, version)) {
            throw ExceptionFactory.alreadyExists(
                    ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Routing", code + "/" + version);
        }

        RoutingHeader routing = RoutingHeader.builder()
                .company(item.getCompany())
                .item(item)
                .code(code)
                .routingVersion(version)
                .status(RoutingStatus.DRAFT)
                .note(trimToNull(request.note()))
                .build();

        // Resolve every work center and validate B_wc2 (same plant across all operations) BEFORE
        // building any entity (rule C9) — a routing whose operations span two plants must never
        // reach the repository.
        Set<Integer> sequences = new HashSet<>();
        Map<Integer, WorkCenter> workCentersBySequence = new LinkedHashMap<>();
        for (RoutingOperationRequest operationRequest : request.operations()) {
            if (!sequences.add(operationRequest.sequence())) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.BUSINESS_RULE_VIOLATION,
                        "Duplicate operation sequence: " + operationRequest.sequence());
            }
            workCentersBySequence.put(operationRequest.sequence(),
                    workCenterLookupService.getActiveWorkCenter(operationRequest.workCenterId()));
        }
        ensureOperationsShareOnePlant(workCentersBySequence.values());

        for (RoutingOperationRequest operationRequest : request.operations()) {
            routing.getOperations().add(buildOperation(
                    routing, operationRequest, workCentersBySequence.get(operationRequest.sequence())));
        }
        return mapper.toResponse(routingHeaderRepository.save(routing));
    }

    /**
     * Activating makes this the one routing production will snapshot for the item, so the previous
     * {@code ACTIVE} revision of the same item is stood down in the same transaction — same rule as
     * {@code BomService.activateBom} (invariant B7).
     */
    @Transactional
    @PreAuthorize("@routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_MANAGE', #routingId)")
    @Auditable(action = AuditAction.ROUTING_ACTIVATED, entityType = "RoutingHeader", entityIdExpression = "routingId.toString()")
    public RoutingResponse activate(UUID routingId) {
        RoutingHeader candidate = findRouting(routingId);
        if (!candidate.isDraft()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Only DRAFT routings can be activated");
        }
        if (candidate.getOperations().isEmpty()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot activate routing without operations");
        }

        routingHeaderRepository.findByCompanyCompanyIdAndItemItemIdAndStatus(
                        candidate.getCompany().getCompanyId(),
                        candidate.getItem().getItemId(),
                        RoutingStatus.ACTIVE)
                .filter(active -> !active.getRoutingId().equals(candidate.getRoutingId()))
                .ifPresent(active -> {
                    active.deactivate();
                    routingHeaderRepository.saveAndFlush(active);
                });

        candidate.activate();
        return mapper.toResponse(routingHeaderRepository.save(candidate));
    }

    @Transactional
    @PreAuthorize("@routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_MANAGE', #routingId)")
    @Auditable(action = AuditAction.ROUTING_DEACTIVATED, entityType = "RoutingHeader", entityIdExpression = "routingId.toString()")
    public RoutingResponse deactivate(UUID routingId) {
        RoutingHeader routing = findRouting(routingId);
        routing.deactivate();
        return mapper.toResponse(routingHeaderRepository.save(routing));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ROUTING_READ', 'COMPANY', #companyId)")
    public PageResult<RoutingResponse> list(UUID companyId, UUID itemId, RoutingStatus status, Pageable pageable) {
        return PageResult.from(routingHeaderRepository.search(companyId, itemId, status, pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_READ', #routingId)")
    public RoutingResponse get(UUID routingId) {
        return mapper.toResponse(findRouting(routingId));
    }

    private RoutingOperation buildOperation(RoutingHeader routing, RoutingOperationRequest request, WorkCenter workCenter) {
        return RoutingOperation.builder()
                .routing(routing)
                .sequence(request.sequence())
                .name(request.name().trim())
                .workCenter(workCenter)
                .setupMinutes(request.setupMinutes())
                .runMinutesPerUnit(request.runMinutesPerUnit())
                .build();
    }

    /**
     * Bất biến B_wc2 (module/workcenter/CLAUDE.md): every operation in one routing must reference a
     * work center of the same plant. Routing itself stays company-level (no schema change), so this
     * is the only place that enforces the consequence of Work Center being per-plant — 422, not 409:
     * it is invalid <em>input</em>, not a status-machine conflict (error-handling.md §5.3).
     */
    private void ensureOperationsShareOnePlant(Collection<WorkCenter> workCenters) {
        long distinctPlants = workCenters.stream()
                .map(workCenter -> workCenter.getPlant().getPlantId())
                .distinct()
                .count();
        if (distinctPlants > 1) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "All routing operations must reference work centers of the same plant");
        }
    }

    private RoutingHeader findRouting(UUID routingId) {
        return routingHeaderRepository.findWithOperationsByRoutingId(routingId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Routing", routingId));
    }

    private void ensureManufacturableItem(Item item) {
        if (item.getType() != ItemType.WIP && item.getType() != ItemType.FINISHED_GOOD) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Routing item must be WIP or FINISHED_GOOD");
        }
    }

    private void ensureItemBelongsToCompany(Item item, UUID companyId) {
        if (!item.getCompany().getCompanyId().equals(companyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item must belong to the routing company");
        }
    }

    private String normalize(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, fieldName + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
