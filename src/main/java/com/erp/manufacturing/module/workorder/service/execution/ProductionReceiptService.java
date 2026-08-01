package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryAdjustCommand;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.LotStatusChangeCommand;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptCandidateResponse;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptPostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptQcDispositionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptRejectRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
import com.erp.manufacturing.module.workorder.repository.OpenReceiptQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.QualityDispositionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderDemandAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Production receipts move through four steps (spec §6.1):
 * <ol>
 *   <li>{@code post} creates a {@code DRAFT} document and touches no inventory;</li>
 *   <li>{@code submit} hands it to the approver as {@code PENDING_APPROVAL}, still no inventory;</li>
 *   <li>{@code approve} creates the RECEIVE movements (new lots open in {@link LotStatus#HOLD})
 *       and advances the work order, or {@code reject} closes the document with no stock impact;</li>
 *   <li>{@code qcDisposition} passes judgement on the output: {@code AVAILABLE} or {@code REJECTED}.
 *       {@code AVAILABLE} is also the single trigger for sales order fulfilment (spec §7.1, F6).</li>
 * </ol>
 *
 * <p>The QC step has two shapes, because output that is not lot-tracked has no lot to carry
 * {@code HOLD}: see {@link #dispositionLots} and {@link #dispositionWithoutLots}.
 */
@Service
@RequiredArgsConstructor
public class ProductionReceiptService {

    /**
     * Statuses that may still take a receipt — the query-side spelling of {@code canReceipt()}
     * (B13 branch 3), which a JPQL query cannot call.
     *
     * <p>🔴 {@code COMPLETED} belongs here and its absence is debt #25 all over again: the shop floor
     * is done, but {@code completedQuantity} — how much reached the racks — lags behind, so the last
     * receipt of every work order happens against a {@code COMPLETED} one. Contrast
     * {@code ProductionExecutionService.EXECUTION_CANDIDATE_STATUSES}, which correctly stops at
     * {@code IN_PROGRESS}: nothing further may be <em>produced</em>, but what was produced still has
     * to be put away.
     */
    private static final List<WorkOrderStatus> RECEIPT_CANDIDATE_STATUSES = List.of(
            WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED);

    private final ProductionReceiptRepository receiptRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ProductionReceiptLineRepository receiptLineRepository;
    private final ProductionExecutionRepository executionRepository;
    private final QualityDispositionRepository dispositionRepository;
    private final InventoryMovementService movementService;
    private final WipTransactionService wipTransactionService;
    private final ItemLookupService itemLookupService;
    private final UserLookupService userLookupService;
    private final SecurityAuditorAware auditorAware;
    private final WorkOrderDemandAllocationService allocationService;
    private final WorkOrderExecutionSupport support;
    private final ManufacturingExecutionMapper mapper;
    private final IdempotencySupport idempotency;
    private final TraceIdProvider traceIdProvider;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_CREATED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse post(UUID workOrderId,
                                          ProductionReceiptPostRequest request,
                                          String idempotencyKey) {
        return postInternal(workOrderId, request, idempotencyKey);
    }

    /**
     * Hands a draft to the approver. Still no inventory impact — this only changes who the
     * document is waiting on.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_SUBMITTED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse submit(UUID workOrderId, UUID receiptId) {
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        if (!receipt.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a DRAFT receipt can be submitted");
        }
        support.ensureReceiptable(receipt.getWorkOrder());
        receipt.submit(Instant.now());
        return toResponse(receiptRepository.save(receipt));
    }

    /**
     * Approves a pending receipt: this is where stock is actually created.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_APPROVE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_POSTED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse approve(UUID workOrderId, UUID receiptId) {
        ProductionReceipt receipt = findPendingReceipt(workOrderId, receiptId);
        WorkOrder workOrder = receipt.getWorkOrder();
        support.ensureReceiptable(workOrder);

        BigDecimal alreadyReceipted = workOrder.getCompletedQuantity();
        BigDecimal totalReceived = BigDecimal.ZERO;
        int index = 0;
        for (ProductionReceiptLine line : receipt.getLines()) {
            index++;
            InventoryMovementResult movementResult = movementService.receive(new InventoryReceiveCommand(
                    workOrder.getProductItem().getItemId(),
                    line.getWarehouse().getWarehouseId(),
                    line.getLot() != null ? line.getLot().getLotId() : null,
                    line.getRequestedLotCode(),
                    line.getQuantity(),
                    line.getReason(),
                    WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE,
                    workOrder.getWorkOrderId().toString()),
                    idempotency.childKey(receipt.getIdempotencyKey() + ":approve", index),
                    LotStatus.HOLD);

            StockMovement movement = movementResult.movement();
            line.setStockMovement(movement);
            line.setLot(movement.getLot());
            totalReceived = totalReceived.add(line.getQuantity());
        }

        Instant now = Instant.now();
        // F5 inversion (CLAUDE.md §0.5): approval records that output was warehoused, and nothing
        // more. It does NOT mark the work order in progress and does NOT complete it — only
        // Production Execution can do that. completedQuantity keeps its original meaning,
        // "how much has been receipted", which is exactly what this step advances.
        workOrder.setCompletedQuantity(alreadyReceipted.add(totalReceived));
        receipt.approve(now, auditorAware.getCurrentAuditor().orElse(null));
        receipt.setSourceWipTraceIds(sourceWipTraceIds(workOrder, alreadyReceipted, totalReceived));

        ProductionReceipt saved = receiptRepository.save(receipt);
        wipTransactionService.recordOutputReceipted(workOrder, totalReceived, saved.getReceiptId());
        return toResponse(saved);
    }

    /**
     * Which shop-floor reports this receipt drew from (spec §6.4). Receipts consume production FIFO,
     * so the sources are the executions whose cumulative good quantity overlaps the interval
     * {@code [alreadyReceipted, alreadyReceipted + received)}.
     */
    private String sourceWipTraceIds(WorkOrder workOrder, BigDecimal alreadyReceipted, BigDecimal received) {
        BigDecimal rangeEnd = alreadyReceipted.add(received);
        BigDecimal cumulative = BigDecimal.ZERO;
        List<String> traceIds = new ArrayList<>();
        for (ProductionExecution execution : executionRepository
                .findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrder.getWorkOrderId())) {
            BigDecimal start = cumulative;
            cumulative = cumulative.add(execution.getGoodQuantity());
            boolean overlaps = start.compareTo(rangeEnd) < 0 && cumulative.compareTo(alreadyReceipted) > 0;
            if (overlaps && execution.getTraceId() != null) {
                traceIds.add(execution.getTraceId());
            }
        }
        return traceIds.isEmpty() ? null : String.join(",", traceIds);
    }

    /**
     * Rejects a pending receipt. No inventory movement is created and the work order is untouched.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_APPROVE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_REJECTED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse reject(UUID workOrderId,
                                            UUID receiptId,
                                            ProductionReceiptRejectRequest request) {
        ProductionReceipt receipt = findPendingReceipt(workOrderId, receiptId);
        String reason = support.trimToNull(request.reason());
        if (reason == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.APPROVAL_REASON_REQUIRED,
                    "Reject reason is required");
        }
        receipt.reject(Instant.now(), reason);
        return toResponse(receiptRepository.save(receipt));
    }

    /**
     * Records the QC decision on an approved receipt. What that does to stock depends on whether the
     * output is carried by a lot — {@link #dispositionLots} vs {@link #dispositionWithoutLots} — but
     * the gates in front of it are the same either way: {@code APPROVED} only, once only, reason
     * required (B38).
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_QUALITY_DISPOSITION', #workOrderId)")
    @Auditable(action = AuditAction.QC_DISPOSITION_RECORDED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse qcDisposition(UUID workOrderId,
                                                   UUID receiptId,
                                                   ProductionReceiptQcDispositionRequest request) {
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        if (!receipt.isApproved()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only an APPROVED receipt can receive a QC disposition");
        }
        if (receipt.isQcDecided()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "QC disposition has already been recorded for this receipt");
        }
        String reason = support.trimToNull(request.reason());
        if (reason == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.APPROVAL_REASON_REQUIRED,
                    "QC disposition reason is required");
        }

        Instant now = Instant.now();
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        // The condition is per line, not a flag on the item: what decides the path is whether
        // approval actually opened a lot to carry HOLD.
        List<ProductionReceiptLine> lotLines = receipt.getLines().stream()
                .filter(line -> line.getLot() != null)
                .toList();
        BigDecimal dispositionedQuantity = lotLines.isEmpty()
                ? dispositionWithoutLots(receipt, request.result(), reason)
                : dispositionLots(receipt, lotLines, request.result(), reason, now, actor);

        // Spec §7.1: fulfilment is driven by QC release and nothing else. REJECTED output stays on
        // hand but unusable, so it must never touch an allocation or a sales order (F6).
        if (request.result() == QualityDispositionResult.AVAILABLE) {
            allocationService.fulfill(receipt.getWorkOrder(), dispositionedQuantity);
        }

        receipt.recordQcDecision(request.result(), reason, now, actor);
        return toResponse(receiptRepository.save(receipt));
    }

    /**
     * QC on lot-tracked output: every output lot leaves {@link LotStatus#HOLD} and the decision is
     * also written per lot to {@code quality_dispositions}. No balance changes — availability moves
     * because the availability queries filter on lot status (B39).
     *
     * @return the quantity QC has just judged, which is what fulfilment draws on
     */
    private BigDecimal dispositionLots(ProductionReceipt receipt,
                                       List<ProductionReceiptLine> lotLines,
                                       QualityDispositionResult result,
                                       String reason,
                                       Instant now,
                                       UUID actor) {
        // Validate every lot before touching any of them, so a half-disposed receipt is impossible
        // and two lines sharing a lot do not fail each other (C9 fail fast).
        for (ProductionReceiptLine line : lotLines) {
            if (line.getLot().getStatus() != LotStatus.HOLD) {
                throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE,
                        "Lot is not on hold and cannot be dispositioned: " + line.getLot().getLotCode());
            }
        }

        Set<UUID> decidedLotIds = new LinkedHashSet<>();
        BigDecimal dispositionedQuantity = BigDecimal.ZERO;
        int index = 0;
        for (ProductionReceiptLine line : lotLines) {
            index++;
            movementService.changeLotStatus(new LotStatusChangeCommand(
                    line.getItem().getItemId(),
                    line.getWarehouse().getWarehouseId(),
                    line.getLot().getLotId(),
                    result.lotStatus(),
                    line.getQuantity(),
                    reason,
                    WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE,
                    receipt.getWorkOrder().getWorkOrderId().toString()),
                    idempotency.childKey(receipt.getIdempotencyKey() + ":qc", index));
            dispositionedQuantity = dispositionedQuantity.add(line.getQuantity());

            if (decidedLotIds.add(line.getLot().getLotId())) {
                dispositionRepository.save(QualityDisposition.builder()
                        .receipt(receipt)
                        .lot(line.getLot())
                        .result(result)
                        .reason(reason)
                        .decidedBy(actor)
                        .decidedAt(now)
                        .build());
            }
        }
        return dispositionedQuantity;
    }

    /**
     * QC on output that is not lot-tracked (D5, debt #17). Without a lot there is nothing to move out
     * of {@code HOLD} — approval put the goods straight into free stock — so the verdict is recorded
     * on the <em>receipt</em> ({@code qcResult}/{@code qcReason}/{@code qcAt}/{@code qcBy}) and no
     * {@code quality_dispositions} row is written: that table is one row per lot by definition, and a
     * row without a lot would contradict it. A QC report built from that table therefore does not see
     * these receipts.
     *
     * <p>{@code AVAILABLE} changes no balance and creates no movement — the goods have been usable
     * since approval, so the only thing this decision unlocks is fulfilment. {@code REJECTED} must
     * change stock, because defective goods that are already usable would otherwise ship: it
     * withdraws them with an {@code ADJUST_OUT} movement (B39, rewritten in D5).
     *
     * <p>If the goods have already left the warehouse the withdrawal fails with
     * {@code INSUFFICIENT_AVAILABLE_STOCK} and the whole disposition rolls back. That is deliberate:
     * defective output already issued or shipped is a real incident needing a human, not something to
     * paper over by rejecting a quantity the ledger cannot give back.
     */
    private BigDecimal dispositionWithoutLots(ProductionReceipt receipt,
                                              QualityDispositionResult result,
                                              String reason) {
        BigDecimal dispositionedQuantity = BigDecimal.ZERO;
        int index = 0;
        for (ProductionReceiptLine line : receipt.getLines()) {
            index++;
            if (result == QualityDispositionResult.REJECTED) {
                movementService.adjust(new InventoryAdjustCommand(
                        line.getItem().getItemId(),
                        line.getWarehouse().getWarehouseId(),
                        null,
                        null,
                        line.getQuantity().negate(),
                        reason,
                        WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE,
                        receipt.getWorkOrder().getWorkOrderId().toString()),
                        idempotency.childKey(receipt.getIdempotencyKey() + ":qc-reject", index));
            }
            dispositionedQuantity = dispositionedQuantity.add(line.getQuantity());
        }
        return dispositionedQuantity;
    }

    @Transactional
    public ProductionReceiptResponse postInternal(UUID workOrderId,
                                           ProductionReceiptPostRequest request,
                                           String idempotencyKey) {
        String normalizedKey = idempotency.normalizeKey(idempotencyKey);
        return receiptRepository.findWithLinesByIdempotencyKey(normalizedKey)
                .map(existing -> {
                    idempotency.ensureSamePayload(existing.getPayloadHash(), request);
                    return toResponse(existing);
                })
                .orElseGet(() -> postNew(workOrderId, request, normalizedKey));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    public PageResult<ProductionReceiptResponse> list(UUID workOrderId, Pageable pageable) {
        support.findWorkOrder(workOrderId);
        return toPage(receiptRepository.findByWorkOrderWorkOrderId(workOrderId, pageable));
    }

    /**
     * Flat plant-scoped list (spec §6.2).
     *
     * <p>Authorises on the plant rather than on a work order, exactly like
     * {@code ProductionExecutionService.listCandidates} — there is no single work order id here for
     * {@code hasWorkOrderAccess} to take. The permission is the existing
     * {@code PERM_PRODUCTION_RECEIPT_MANAGE}: {@code V17} seeds one permission per document type and
     * describes it as "Post <em>and read</em> … documents", and every nested read endpoint already
     * uses it. Minting a separate {@code _READ} permission would mean the same data is readable
     * under two different permissions depending on which URL you ask through — the exact
     * inconsistency signal error-handling.md §5.3 calls the strongest one there is.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', 'PLANT', #plantId)")
    public PageResult<ProductionReceiptResponse> listByPlant(UUID plantId,
                                                             ProductionReceiptStatus status,
                                                             Pageable pageable) {
        return toPage(receiptRepository.findByPlant(plantId, status, pageable));
    }

    /**
     * Work orders with finished output still waiting to be warehoused (spec §6.2, invariant B76).
     *
     * <p>The gate itself lives in {@code WorkOrderRepository.findReceiptCandidates}; the batch below
     * exists only so each row can <em>display</em> the same ceiling the query filtered on. That is
     * two queries per page and none per row (rule C14) — {@code availableToReceipt()} on its own
     * does not deduct open receipts, so a mapper alone cannot produce the number.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', 'PLANT', #plantId)")
    public PageResult<ProductionReceiptCandidateResponse> listCandidates(UUID plantId, Pageable pageable) {
        Page<WorkOrder> page = workOrderRepository.findReceiptCandidates(
                plantId, RECEIPT_CANDIDATE_STATUSES, pageable);
        List<UUID> workOrderIds = page.getContent().stream()
                .map(WorkOrder::getWorkOrderId)
                .toList();
        Map<UUID, BigDecimal> openByWorkOrder = workOrderIds.isEmpty()
                ? Map.of()
                : receiptRepository.sumOpenQuantityByWorkOrderIds(workOrderIds).stream()
                .collect(Collectors.toMap(
                        OpenReceiptQuantityProjection::getWorkOrderId,
                        OpenReceiptQuantityProjection::getOpenQuantity));
        return PageResult.from(page.map(workOrder -> mapper.toReceiptCandidateResponse(
                workOrder,
                openByWorkOrder.getOrDefault(workOrder.getWorkOrderId(), BigDecimal.ZERO))));
    }

    /** Loads the lines and author names of a page of receipts in one batch query each (rule C14). */
    private PageResult<ProductionReceiptResponse> toPage(Page<ProductionReceipt> page) {
        List<UUID> receiptIds = page.getContent().stream()
                .map(ProductionReceipt::getReceiptId)
                .toList();
        Map<UUID, List<ProductionReceiptLine>> linesByReceipt = receiptIds.isEmpty()
                ? Map.of()
                : receiptLineRepository.findByReceiptReceiptIdIn(receiptIds).stream()
                .collect(Collectors.groupingBy(line -> line.getReceipt().getReceiptId()));
        Map<UUID, String> usernames = usernamesOf(page.getContent());
        return PageResult.from(page.map(receipt -> mapper.toResponse(receipt, linesByReceipt, usernames)));
    }

    /**
     * Creates the receipt in {@code DRAFT}. Deliberately does not create any stock movement, does
     * not increase {@code completedQuantity} and does not record WIP — those all happen in
     * {@link #approve(UUID, UUID)}.
     */
    private ProductionReceiptResponse postNew(UUID workOrderId,
                                              ProductionReceiptPostRequest request,
                                              String normalizedKey) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureReceiptable(workOrder);
        ProductionReceipt receipt = ProductionReceipt.builder()
                .workOrder(workOrder)
                .status(ProductionReceiptStatus.DRAFT)
                .code(generateCode())
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .traceId(traceIdProvider.currentTraceId())
                .note(support.trimToNull(request.note()))
                .build();

        Warehouse warehouse = support.findActiveWarehouseInPlant(
                request.destinationWarehouseId(), workOrder.getPlant());
        BigDecimal quantity = support.requirePositive(request.quantity(), "Receipt quantity");

        // Invariant B16, rewritten in F5: the ceiling is what the shop floor actually produced and
        // has not been receipted yet — no longer plannedQuantity - completedQuantity. Receipts that
        // are still open (DRAFT / PENDING_APPROVAL) have already claimed part of it.
        BigDecimal remaining = workOrder.availableToReceipt()
                .subtract(receiptRepository.sumOpenQuantityByWorkOrderId(workOrderId));
        if (quantity.compareTo(remaining) > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                    "Receipt quantity exceeds the good quantity produced and not yet receipted");
        }
        // Fail here rather than at approval: without a lot the output can never leave HOLD,
        // so a lot-tracked receipt with no lot would be un-QC-able (spec §6.3).
        if (workOrder.getProductItem().isLotTracked()
                && request.lotId() == null
                && support.trimToNull(request.lotNumber()) == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.LOT_REQUIRED,
                    "Lot-tracked output requires a lot number or lot id");
        }

        receipt.getLines().add(ProductionReceiptLine.builder()
                .receipt(receipt)
                .item(workOrder.getProductItem())
                .warehouse(warehouse)
                .lot(request.lotId() != null
                        ? itemLookupService.getLotForItem(workOrder.getProductItem(), request.lotId())
                        : null)
                .requestedLotCode(support.trimToNull(request.lotNumber()))
                .quantity(quantity)
                .reason(support.trimToNull(request.reason()))
                .build());

        return toResponse(receiptRepository.save(receipt));
    }

    /** Resolves the author/approver/QC usernames of a single receipt in one query. */
    private ProductionReceiptResponse toResponse(ProductionReceipt receipt) {
        return mapper.toResponse(receipt, usernamesOf(List.of(receipt)));
    }

    private Map<UUID, String> usernamesOf(List<ProductionReceipt> receipts) {
        List<UUID> actorIds = receipts.stream()
                .flatMap(receipt -> Stream.of(receipt.getCreatedBy(), receipt.getApprovedBy(), receipt.getQcBy()))
                .filter(Objects::nonNull)
                .toList();
        return userLookupService.findUsernames(actorIds);
    }

    private ProductionReceipt findPendingReceipt(UUID workOrderId, UUID receiptId) {
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        if (!receipt.isPendingApproval()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only receipts pending approval can be approved or rejected");
        }
        return receipt;
    }

    private ProductionReceipt findReceiptOfWorkOrder(UUID workOrderId, UUID receiptId) {
        ProductionReceipt receipt = receiptRepository.findWithLinesByReceiptId(receiptId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Production receipt", receiptId));
        if (!receipt.getWorkOrder().getWorkOrderId().equals(workOrderId)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Production receipt does not belong to the requested work order");
        }
        return receipt;
    }

    /**
     * Document number (spec §6.4). Derived from a UUID rather than a database sequence: receipts are
     * created concurrently across plants and a shared sequence would be a contention point for a
     * value nobody sorts on.
     */
    private String generateCode() {
        return "PR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
