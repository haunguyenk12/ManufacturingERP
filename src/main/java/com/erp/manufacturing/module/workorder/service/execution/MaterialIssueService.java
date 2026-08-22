package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryIssueCommand;
import com.erp.manufacturing.module.inventory.service.InventoryLotLookupService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueFlatRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueDecisionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssuePostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
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
    private final InventoryLotLookupService inventoryLotLookupService;

    @Autowired(required = false)
    private ProductionExecutionRepository productionExecutionRepository;

    @Autowired(required = false)
    private SecurityAuditorAware auditorAware;

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

    /**
     * One work order's issue history.
     *
     * @param status optional filter; {@code PENDING_APPROVAL} is this work order's Over-BOM approval
     *        queue. Deliberately a single method rather than a no-status convenience overload
     *        delegating to this one: a service method calling its own sibling goes through
     *        {@code this}, not the Spring proxy, so the callee's {@code @PreAuthorize} never runs
     *        (the trap recorded in CLAUDE.md §0.19). The controller passes {@code null} instead.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #workOrderId)")
    public PageResult<MaterialIssueResponse> list(UUID workOrderId, MaterialIssueStatus status, Pageable pageable) {
        support.findWorkOrder(workOrderId);
        return toPage(materialIssueRepository.findByWorkOrder(workOrderId, status, pageable));
    }

    /**
     * Flat plant-scoped history (spec §4.1). {@code workOrderId} narrows it further and is optional.
     *
     * <p>Authorises on the plant rather than on one work order — there is no single work order id
     * here to authorise against. Reuses {@code PERM_MATERIAL_ISSUE_MANAGE} for the same reason
     * {@link ProductionReceiptService#listByPlant} reuses its own MANAGE permission: {@code V17}
     * seeds one permission per document type covering post and read alike.
     *
     * @param status optional filter; {@code PENDING_APPROVAL} is the plant-wide Over-BOM approval
     *        queue a manager works from.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', 'PLANT', #plantId)")
    public PageResult<MaterialIssueResponse> listByPlant(UUID plantId, UUID workOrderId,
                                                         MaterialIssueStatus status, Pageable pageable) {
        return toPage(materialIssueRepository.findByPlant(plantId, workOrderId, status, pageable));
    }

    /**
     * Loads the lines, author names and lot codes of a page of issues in one batch query each
     * (rule C14).
     */
    private PageResult<MaterialIssueResponse> toPage(Page<MaterialIssue> page) {
        List<UUID> issueIds = page.getContent().stream()
                .map(MaterialIssue::getIssueId)
                .toList();
        Map<UUID, List<MaterialIssueLine>> linesByIssue = issueIds.isEmpty()
                ? Map.of()
                : materialIssueLineRepository.findByIssueIssueIdIn(issueIds).stream()
                .collect(Collectors.groupingBy(line -> line.getIssue().getIssueId()));
        Map<UUID, String> usernames = usernamesOf(page.getContent());
        Map<UUID, String> lotCodes = lotCodesOf(linesByIssue.values().stream()
                .flatMap(List::stream)
                .toList());
        return PageResult.from(page.map(
                issue -> mapper.toResponse(issue, linesByIssue, usernames, lotCodes)));
    }

    /** Resolves the author username and the lot codes of a single issue document in one query each. */
    private MaterialIssueResponse toResponse(MaterialIssue issue) {
        return mapper.toResponse(issue, usernamesOf(List.of(issue)), lotCodesOf(issue.getLines()));
    }

    /** One query for the whole page (rule C14) — never one lookup per row. */
    private Map<UUID, String> usernamesOf(List<MaterialIssue> issues) {
        return userLookupService.findUsernames(issues.stream()
                .map(MaterialIssue::getCreatedBy)
                .filter(Objects::nonNull)
                .toList());
    }

    /**
     * Lot codes for lines that named a lot by id but are not linked to one yet — a PENDING_APPROVAL
     * line only gets its {@code lot} when approval posts the movement. Lines that already carry a
     * lot, or that named it by code, need nothing looked up, so a page of ordinary POSTED history
     * asks for an empty set and never reaches the database (rule C14).
     */
    private Map<UUID, String> lotCodesOf(List<MaterialIssueLine> lines) {
        return inventoryLotLookupService.findLotCodes(lines.stream()
                .filter(line -> line.getLot() == null && line.getRequestedLotCode() == null)
                .map(MaterialIssueLine::getRequestedLotId)
                .filter(Objects::nonNull)
                .toList());
    }

    private MaterialIssueResponse postNew(UUID workOrderId,
                                          MaterialIssuePostRequest request,
                                          String normalizedKey) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureExecutable(workOrder);
        if (requiresOverBomApproval(workOrder, request)) {
            return requestOverBomIssue(workOrder, request, normalizedKey);
        }
        Instant now = Instant.now();
        MaterialIssue issue = MaterialIssue.builder()
                .workOrder(workOrder)
                .status(MaterialIssueStatus.POSTED)
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .traceId(traceIdProvider.currentTraceId())
                .note(support.trimToNull(request.note()))
                .requestedAt(now)
                .postedAt(now)
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
                        lineRequest.serialId(),
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
                        lineRequest.serialId(),
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
                    .serial(movement.getSerial())
                    .quantity(quantity)
                    .overIssue(overIssue)
                    .overrideReason(support.trimToNull(lineRequest.overrideReason()))
                    .reasonCode(lineRequest.reasonCode())
                    .issueReason(support.trimToNull(lineRequest.reason()))
                    .sourceExecution(resolveSourceExecution(workOrder, lineRequest.sourceExecutionId()))
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
        throw ExceptionFactory.custom(BusinessErrorCode.OVER_BOM_APPROVAL_REQUIRED,
                "Over-BOM material must be requested and approved before stock is posted");
    }

    private boolean requiresOverBomApproval(WorkOrder workOrder, MaterialIssuePostRequest request) {
        return request.lines().stream().anyMatch(line -> {
            WorkOrderComponentLine component = support.findComponentLine(workOrder, line.componentLineId());
            return support.requirePositive(line.quantity(), "Issue quantity")
                    .compareTo(component.remainingQuantity()) > 0;
        });
    }

    private MaterialIssueResponse requestOverBomIssue(WorkOrder workOrder,
                                                       MaterialIssuePostRequest request,
                                                       String normalizedKey) {
        Instant now = Instant.now();
        MaterialIssue issue = MaterialIssue.builder()
                .workOrder(workOrder)
                .status(MaterialIssueStatus.PENDING_APPROVAL)
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .traceId(traceIdProvider.currentTraceId())
                .note(support.trimToNull(request.note()))
                .requestedAt(now)
                .build();

        for (MaterialIssueLineRequest lineRequest : request.lines()) {
            WorkOrderComponentLine component = support.findComponentLine(workOrder, lineRequest.componentLineId());
            Warehouse warehouse = support.findActiveWarehouseInPlant(lineRequest.warehouseId(), workOrder.getPlant());
            BigDecimal quantity = support.requirePositive(lineRequest.quantity(), "Issue quantity");
            boolean overIssue = quantity.compareTo(component.remainingQuantity()) > 0;
            if (overIssue && (!StringUtils.hasText(lineRequest.overrideReason()) || lineRequest.reasonCode() == null)) {
                throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                        "reasonCode and overrideReason are required for an Over-BOM request");
            }
            if (lineRequest.serialId() != null) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Serial-tracked Over-BOM material is deferred");
            }
            MaterialReservation reservation = lineRequest.reservationId() == null
                    ? null
                    : reservationService.findActiveReservationForIssue(
                            workOrder.getWorkOrderId(), lineRequest.reservationId());
            if (reservation != null) {
                validateReservationForIssue(reservation, component, warehouse, lineRequest, quantity);
            }
            requireLotWhenApprovalWillNeedOne(component, reservation, lineRequest);
            issue.getLines().add(MaterialIssueLine.builder()
                    .issue(issue)
                    .componentLine(component)
                    .reservation(reservation)
                    .item(component.getComponentItem())
                    .warehouse(warehouse)
                    .quantity(quantity)
                    .overIssue(overIssue)
                    .overrideReason(support.trimToNull(lineRequest.overrideReason()))
                    .requestedLotId(lineRequest.lotId())
                    .requestedLotCode(support.trimToNull(lineRequest.lotNumber()))
                    .requestedSerialId(lineRequest.serialId())
                    .issueReason(support.trimToNull(lineRequest.reason()))
                    .reasonCode(lineRequest.reasonCode())
                    .sourceExecution(resolveSourceExecution(workOrder, lineRequest.sourceExecutionId()))
                    .build());
        }
        return toResponse(materialIssueRepository.save(issue));
    }

    /**
     * A lot-tracked component can only leave the warehouse from a named lot. An Over-BOM line has no
     * reservation to inherit one from, so if the request carries no lot the movement at approval time
     * is guaranteed to fail — and the request would sit in {@code PENDING_APPROVAL} forever, rejected
     * by a manager who cannot fix it and abandoned by the operator who could. Fail here instead
     * (rule C9), while the person who knows which lot they took is still on the screen.
     *
     * <p>The check does not replace the authoritative one inside {@code InventoryMovementService} at
     * approval: stock can change between request and decision, and that recheck runs in the posting
     * transaction. This one only stops a request that can never succeed from being stored at all.
     */
    private void requireLotWhenApprovalWillNeedOne(WorkOrderComponentLine component,
                                                   MaterialReservation reservation,
                                                   MaterialIssueLineRequest lineRequest) {
        if (reservation != null || !component.getComponentItem().isLotTracked()) {
            return;
        }
        if (lineRequest.lotId() == null && !StringUtils.hasText(lineRequest.lotNumber())) {
            throw ExceptionFactory.custom(ValidationErrorCode.LOT_REQUIRED,
                    "Over-BOM material for a lot-tracked component must name the lot it is taken from");
        }
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_APPROVE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_APPROVED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse approve(UUID workOrderId, UUID issueId) {
        MaterialIssue issue = findPendingIssue(workOrderId, issueId);
        WorkOrder workOrder = issue.getWorkOrder();
        support.ensureExecutable(workOrder);
        BigDecimal totalIssued = BigDecimal.ZERO;
        int index = 0;
        for (MaterialIssueLine line : issue.getLines()) {
            index++;
            MaterialReservation reservation = line.getReservation() == null ? null
                    : reservationService.findActiveReservationForIssue(
                            workOrderId, line.getReservation().getReservationId());
            MaterialIssueLineRequest storedRequest = new MaterialIssueLineRequest(
                    line.getComponentLine().getComponentLineId(),
                    reservation == null ? null : reservation.getReservationId(),
                    line.getWarehouse().getWarehouseId(),
                    line.getRequestedLotId(), line.getRequestedLotCode(), line.getRequestedSerialId(),
                    line.getQuantity(), line.getIssueReason(), line.getOverrideReason(),
                    line.getReasonCode(),
                    line.getSourceExecution() == null ? null : line.getSourceExecution().getProductionExecutionId());
            if (reservation != null) {
                validateReservationForIssue(reservation, line.getComponentLine(), line.getWarehouse(),
                        storedRequest, line.getQuantity());
            }
            InventoryMovementResult result = reservation == null
                    ? movementService.issue(issueCommand(
                            line.getComponentLine(), line.getWarehouse(), line.getRequestedLotId(),
                            line.getRequestedLotCode(), line.getRequestedSerialId(), line.getQuantity(),
                            line.getIssueReason(), workOrder),
                            idempotency.childKey(issue.getIdempotencyKey() + ":approve", index))
                    : movementService.issueReserved(issueCommand(
                            line.getComponentLine(), line.getWarehouse(),
                            reservation.getLot() == null ? null : reservation.getLot().getLotId(),
                            null, line.getRequestedSerialId(), line.getQuantity(), line.getIssueReason(), workOrder),
                            idempotency.childKey(issue.getIdempotencyKey() + ":approve", index));
            StockMovement movement = result.movement();
            line.setStockMovement(movement);
            line.setLot(movement.getLot());
            line.setSerial(movement.getSerial());
            if (result.created()) {
                if (reservation != null) {
                    reservation.consume(line.getQuantity());
                }
                line.getComponentLine().addIssuedQuantity(line.getQuantity());
                workOrder.markInProgress();
                costAccumulatorService.accumulateMaterialCost(
                        workOrder, line.getComponentLine().getComponentItem(), line.getQuantity());
            }
            totalIssued = totalIssued.add(line.getQuantity());
        }
        issue.markPosted(Instant.now(), auditorAware == null
                ? null : auditorAware.getCurrentAuditor().orElse(null));
        MaterialIssue saved = materialIssueRepository.save(issue);
        wipTransactionService.recordMaterialIssued(workOrder, totalIssued, saved.getIssueId());
        return toResponse(saved);
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_APPROVE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_REJECTED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse reject(UUID workOrderId, UUID issueId, MaterialIssueDecisionRequest request) {
        MaterialIssue issue = findPendingIssue(workOrderId, issueId);
        String reason = request == null ? null : support.trimToNull(request.reason());
        if (reason == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.APPROVAL_REASON_REQUIRED,
                    "Reject reason is required");
        }
        issue.reject(Instant.now(), auditorAware == null
                ? null : auditorAware.getCurrentAuditor().orElse(null), reason);
        return toResponse(materialIssueRepository.save(issue));
    }

    private MaterialIssue findPendingIssue(UUID workOrderId, UUID issueId) {
        MaterialIssue issue = materialIssueRepository.findWithLinesByIssueId(issueId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Material issue", issueId));
        if (!issue.getWorkOrder().getWorkOrderId().equals(workOrderId) || !issue.isPendingApproval()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a PENDING_APPROVAL issue of this work order can be decided");
        }
        return issue;
    }

    private ProductionExecution resolveSourceExecution(WorkOrder workOrder, UUID executionId) {
        if (executionId == null || productionExecutionRepository == null) {
            return null;
        }
        ProductionExecution execution = productionExecutionRepository.findById(executionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Production execution", executionId));
        if (!execution.getWorkOrder().getWorkOrderId().equals(workOrder.getWorkOrderId())
                || execution.getReworkQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "sourceExecutionId must reference a rework execution of the same work order");
        }
        return execution;
    }

    private InventoryIssueCommand issueCommand(WorkOrderComponentLine componentLine,
                                               Warehouse warehouse,
                                               UUID lotId,
                                               String lotCode,
                                               UUID serialId,
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
                workOrder.getWorkOrderId().toString(),
                serialId,
                null);
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
