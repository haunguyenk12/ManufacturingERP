package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryIssueCommand;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueFlatRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssuePostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MaterialIssueService {

    private static final String OVERRIDE_PERMISSION = "PERM_MATERIAL_ISSUE_OVERRIDE";

    private final MaterialIssueRepository materialIssueRepository;
    private final MaterialIssueLineRepository materialIssueLineRepository;
    private final MaterialReservationService reservationService;
    private final InventoryMovementService movementService;
    private final WipTransactionService wipTransactionService;
    private final WorkOrderPermissionGuard workOrderPermissionGuard;
    private final WorkOrderExecutionSupport support;
    private final IdempotencySupport idempotency;
    private final ManufacturingExecutionMapper mapper;
    private final TraceIdProvider traceIdProvider;
    private final UserLookupService userLookupService;
    private final WorkOrderCostAccumulatorService costAccumulatorService;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_POSTED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse post(UUID workOrderId, MaterialIssuePostRequest request, String idempotencyKey) {
        return postInternal(workOrderId, request, idempotencyKey);
    }

    /**
     * Flat form of {@link #post}: the component line comes from the reservation, so the caller only
     * needs the reservation it is consuming (spec §4.1). Over-issue is impossible here by
     * construction — a reservation can never exceed the component requirement.
     *
     * <p>The reservation is resolved <b>without</b> the {@code ACTIVE} check on purpose (debt #24,
     * fixed in D9/D10). It is read here only to translate the flat request into the multi-line shape;
     * the status is enforced by {@link #postNew}, which every document-creating path goes through.
     * Checking it here meant a retry with the same {@code Idempotency-Key} hit
     * {@code STATE_CONFLICT} — the first call had consumed the reservation — instead of replaying the
     * original document, breaking rule {@code R9} at exactly the endpoint the shop floor retries.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #request.workOrderId())")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_POSTED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse postFlat(MaterialIssueFlatRequest request, String idempotencyKey) {
        MaterialReservation reservation = reservationService.findReservationForWorkOrder(
                request.workOrderId(), request.reservationId());
        return postInternal(request.workOrderId(), new MaterialIssuePostRequest(null, List.of(
                new MaterialIssueLineRequest(
                        reservation.getComponentLine().getComponentLineId(),
                        request.reservationId(),
                        reservation.getWarehouse().getWarehouseId(),
                        null,
                        null,
                        request.quantity(),
                        request.reason(),
                        null))), idempotencyKey);
    }

    @Transactional
    public MaterialIssueResponse postInternal(UUID workOrderId, MaterialIssuePostRequest request, String idempotencyKey) {
        String normalizedKey = idempotency.normalizeKey(idempotencyKey);
        return materialIssueRepository.findWithLinesByIdempotencyKey(normalizedKey)
                .map(existing -> {
                    idempotency.ensureSamePayload(existing.getPayloadHash(), request);
                    return toResponse(existing);
                })
                .orElseGet(() -> postNew(workOrderId, request, normalizedKey));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #workOrderId)")
    public PageResult<MaterialIssueResponse> list(UUID workOrderId, Pageable pageable) {
        support.findWorkOrder(workOrderId);
        return toPage(materialIssueRepository.findByWorkOrderWorkOrderId(workOrderId, pageable));
    }

    /**
     * Flat plant-scoped history (spec §4.1). {@code workOrderId} narrows it further and is optional.
     *
     * <p>Authorises on the plant rather than on one work order — there is no single work order id
     * here to authorise against. Reuses {@code PERM_MATERIAL_ISSUE_MANAGE} for the same reason
     * {@link ProductionReceiptService#listByPlant} reuses its own MANAGE permission: {@code V17}
     * seeds one permission per document type covering post and read alike.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', 'PLANT', #plantId)")
    public PageResult<MaterialIssueResponse> listByPlant(UUID plantId, UUID workOrderId, Pageable pageable) {
        return toPage(materialIssueRepository.findByPlant(plantId, workOrderId, pageable));
    }

    /** Loads the lines and author names of a page of issues in one batch query each (rule C14). */
    private PageResult<MaterialIssueResponse> toPage(Page<MaterialIssue> page) {
        List<UUID> issueIds = page.getContent().stream()
                .map(MaterialIssue::getIssueId)
                .toList();
        Map<UUID, List<MaterialIssueLine>> linesByIssue = issueIds.isEmpty()
                ? Map.of()
                : materialIssueLineRepository.findByIssueIssueIdIn(issueIds).stream()
                .collect(Collectors.groupingBy(line -> line.getIssue().getIssueId()));
        Map<UUID, String> usernames = usernamesOf(page.getContent());
        return PageResult.from(page.map(issue -> mapper.toResponse(issue, linesByIssue, usernames)));
    }

    /** Resolves the author username of a single issue document in one query. */
    private MaterialIssueResponse toResponse(MaterialIssue issue) {
        return mapper.toResponse(issue, usernamesOf(List.of(issue)));
    }

    /** One query for the whole page (rule C14) — never one lookup per row. */
    private Map<UUID, String> usernamesOf(List<MaterialIssue> issues) {
        return userLookupService.findUsernames(issues.stream()
                .map(MaterialIssue::getCreatedBy)
                .filter(Objects::nonNull)
                .toList());
    }

    private MaterialIssueResponse postNew(UUID workOrderId,
                                          MaterialIssuePostRequest request,
                                          String normalizedKey) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureExecutable(workOrder);
        MaterialIssue issue = MaterialIssue.builder()
                .workOrder(workOrder)
                .status(MaterialIssueStatus.POSTED)
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .traceId(traceIdProvider.currentTraceId())
                .note(support.trimToNull(request.note()))
                .build();

        BigDecimal totalIssued = BigDecimal.ZERO;
        int index = 0;
        for (MaterialIssueLineRequest lineRequest : request.lines()) {
            index++;
            WorkOrderComponentLine componentLine = support.findComponentLine(workOrder, lineRequest.componentLineId());
            Warehouse warehouse = support.findActiveWarehouseInPlant(lineRequest.warehouseId(), workOrder.getPlant());
            BigDecimal quantity = support.requirePositive(lineRequest.quantity(), "Issue quantity");
            boolean overIssue = isApprovedOverIssue(workOrderId, componentLine, quantity, lineRequest);

            MaterialReservation reservation = null;
            InventoryMovementResult movementResult;
            if (lineRequest.reservationId() != null) {
                reservation = reservationService.findActiveReservationForIssue(workOrderId, lineRequest.reservationId());
                validateReservationForIssue(reservation, componentLine, warehouse, lineRequest, quantity);
                movementResult = movementService.issueReserved(issueCommand(
                        componentLine,
                        warehouse,
                        reservation.getLot() != null ? reservation.getLot().getLotId() : null,
                        null,
                        quantity,
                        lineRequest.reason(),
                        workOrder),
                        idempotency.childKey(normalizedKey, index));
                if (movementResult.created()) {
                    reservation.consume(quantity);
                }
            } else {
                movementResult = movementService.issue(issueCommand(
                        componentLine,
                        warehouse,
                        lineRequest.lotId(),
                        lineRequest.lotNumber(),
                        quantity,
                        lineRequest.reason(),
                        workOrder),
                        idempotency.childKey(normalizedKey, index));
            }

            StockMovement movement = movementResult.movement();
            if (movementResult.created()) {
                componentLine.addIssuedQuantity(quantity);
                workOrder.markInProgress();
                costAccumulatorService.accumulateMaterialCost(workOrder, componentLine.getComponentItem(), quantity);
            }
            issue.getLines().add(MaterialIssueLine.builder()
                    .issue(issue)
                    .componentLine(componentLine)
                    .reservation(reservation)
                    .item(componentLine.getComponentItem())
                    .warehouse(warehouse)
                    .lot(movement.getLot())
                    .quantity(quantity)
                    .overIssue(overIssue)
                    .overrideReason(support.trimToNull(lineRequest.overrideReason()))
                    .stockMovement(movement)
                    .build());
            totalIssued = totalIssued.add(quantity);
        }

        MaterialIssue saved = materialIssueRepository.save(issue);
        wipTransactionService.recordMaterialIssued(workOrder, totalIssued, saved.getIssueId());
        return toResponse(saved);
    }

    /**
     * Gate 1b – issuing beyond the component's remaining BOM requirement is allowed, but only
     * for a user holding {@code PERM_MATERIAL_ISSUE_OVERRIDE} on the work order's plant and only
     * with a justification. The decision is recorded on the issue line itself.
     *
     * @return true when this line issues more than the remaining requirement
     */
    private boolean isApprovedOverIssue(UUID workOrderId,
                                        WorkOrderComponentLine componentLine,
                                        BigDecimal quantity,
                                        MaterialIssueLineRequest lineRequest) {
        BigDecimal remaining = componentLine.remainingQuantity();
        if (quantity.compareTo(remaining) <= 0) {
            return false;
        }
        if (!workOrderPermissionGuard.hasWorkOrderAccess(
                SecurityContextHolder.getContext().getAuthentication(), OVERRIDE_PERMISSION, workOrderId)) {
            throw new AccessDeniedException(
                    OVERRIDE_PERMISSION + " required to issue beyond the component requirement");
        }
        if (!StringUtils.hasText(lineRequest.overrideReason())) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "overrideReason is required when issuing beyond the component requirement");
        }
        log.warn("[OverIssue] workOrder={} componentLine={} quantity={} remaining={} reason={}",
                workOrderId, componentLine.getComponentLineId(), quantity, remaining,
                lineRequest.overrideReason());
        return true;
    }

    private InventoryIssueCommand issueCommand(WorkOrderComponentLine componentLine,
                                               Warehouse warehouse,
                                               UUID lotId,
                                               String lotCode,
                                               BigDecimal quantity,
                                               String reason,
                                               WorkOrder workOrder) {
        return new InventoryIssueCommand(
                componentLine.getComponentItem().getItemId(),
                warehouse.getWarehouseId(),
                lotId,
                lotCode,
                quantity,
                support.trimToNull(reason),
                WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE,
                workOrder.getWorkOrderId().toString());
    }

    private void validateReservationForIssue(MaterialReservation reservation,
                                             WorkOrderComponentLine componentLine,
                                             Warehouse warehouse,
                                             MaterialIssueLineRequest lineRequest,
                                             BigDecimal quantity) {
        if (!reservation.getComponentLine().getComponentLineId().equals(componentLine.getComponentLineId())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Reservation does not belong to the requested component line");
        }
        if (!reservation.getWarehouse().getWarehouseId().equals(warehouse.getWarehouseId())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Reservation warehouse does not match issue warehouse");
        }
        UUID reservationLotId = reservation.getLot() != null ? reservation.getLot().getLotId() : null;
        if (lineRequest.lotId() != null && !lineRequest.lotId().equals(reservationLotId)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Reservation lot does not match issue lot");
        }
        if (quantity.compareTo(reservation.remainingQuantity()) > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.RESERVATION_EXCEEDED,
                    "Issue quantity exceeds remaining reservation quantity");
        }
    }
}

