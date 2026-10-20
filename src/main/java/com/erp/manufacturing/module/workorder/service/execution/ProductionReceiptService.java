package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.SerialStatus;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryAdjustCommand;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.LotStatusChangeCommand;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptCandidateResponse;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptEventResponse;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptPostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptQcDispositionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptReasonRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptResponse;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptUpdateRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptVersionRequest;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.OpenReceiptQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
import com.erp.manufacturing.module.workorder.repository.QualityDispositionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.ReasonRules;
import com.erp.manufacturing.module.workorder.service.WorkOrderDemandAllocationService;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.service.WorkOrderVersions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Production receipts move through these steps (spec §6.1, G9a):
 * <ol>
 *   <li>{@code post} creates a {@code DRAFT} document and touches no inventory; its requester may edit it
 *       ({@code update}) and {@code cancel} it;</li>
 *   <li>{@code submit} hands it to the approver as {@code PENDING_APPROVAL}, still no inventory; the requester may
 *       {@code withdraw} it back to {@code DRAFT} or {@code cancel} it;</li>
 *   <li>{@code approve} creates the RECEIVE movements (new lots open in {@link LotStatus#HOLD})
 *       and advances the work order, or {@code reject} closes the document with no stock impact;</li>
 *   <li>{@code qcDisposition} passes judgement on the output: {@code AVAILABLE} or {@code REJECTED}.
 *       {@code AVAILABLE} is also the single trigger for sales order fulfilment (spec §7.1, F6).</li>
 * </ol>
 *
 * <p>Every command after creation carries the receipt's and the work order's version (FE R19) and moves the work
 * order's version on exactly once (FE Q12): the receipts claim the work order's output, so a change to them is a
 * change to what the work order still has to receipt. An {@code Idempotency-Key} replays the command (G8 shape:
 * replay, then versions, then rules, then {@code touch()}, then one flush).
 *
 * <p>The QC step has two shapes. Lots carry HOLD in {@code InventoryLot.status}; non-tracked output
 * carries it in {@code StockBalance.qualityHoldQuantity}: see {@link #dispositionLots} and
 * {@link #dispositionWithoutLots}.
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

    static final String PERM_READ = "PERM_PRODUCTION_RECEIPT_READ";
    static final String PERM_MANAGE = "PERM_PRODUCTION_RECEIPT_MANAGE";
    static final String PERM_APPROVE = "PERM_PRODUCTION_RECEIPT_APPROVE";
    static final String PERM_QC_READ = "PERM_PRODUCTION_QC_READ";
    static final String PERM_QC = "PERM_QUALITY_DISPOSITION";
    private static final List<String> PERMISSIONS = List.of(PERM_READ, PERM_MANAGE, PERM_APPROVE, PERM_QC_READ, PERM_QC);

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
    private final WorkOrderIdempotency idempotencyRecords;
    private final PermissionGuard permissionGuard;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_CREATED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse post(UUID workOrderId,
                                          ProductionReceiptPostRequest request,
                                          String idempotencyKey) {
        return postInternal(workOrderId, request, idempotencyKey, true);
    }

    /**
     * The internal "complete output" path of {@code WorkOrderService}: it names the work order's output warehouse
     * and neither a version nor a business date, so the work order's version is not checked and the receipt is
     * received on the plant's today.
     */
    @Transactional
    public ProductionReceiptResponse postInternal(UUID workOrderId,
                                                  ProductionReceiptPostRequest request,
                                                  String idempotencyKey) {
        return postInternal(workOrderId, request, idempotencyKey, false);
    }

    private ProductionReceiptResponse postInternal(UUID workOrderId,
                                                   ProductionReceiptPostRequest request,
                                                   String idempotencyKey,
                                                   boolean versioned) {
        String normalizedKey = idempotency.normalizeKey(idempotencyKey);
        return receiptRepository.findWithLinesByIdempotencyKey(normalizedKey)
                .map(existing -> {
                    idempotency.ensureSamePayload(existing.getPayloadHash(), request);
                    return toResponse(existing);
                })
                .orElseGet(() -> postNew(workOrderId, request, normalizedKey, versioned));
    }

    /**
     * G9a: edits a DRAFT (FE §3.3). Only its requester may; quantity, lot number, dates and note change, the
     * source does not. The new quantity is checked against what is unclaimed with this receipt's own claim given
     * back first (FE R04).
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_UPDATED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse update(UUID workOrderId, UUID receiptId, ProductionReceiptUpdateRequest request,
                                            String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(receiptId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.RECEIPT_UPDATE, fingerprint).isPresent()) {
            return toResponse(findReceipt(receiptId));
        }
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        WorkOrder workOrder = receipt.getWorkOrder();
        ensureVersions(receipt, request.expectedVersion(), request.expectedWorkOrderVersion());
        if (!receipt.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, "Only a DRAFT receipt can be edited");
        }
        ensureRequester(receipt, "edit");
        support.ensureReceiptable(workOrder);
        ProductionReceiptLine line = singleLine(receipt);
        BigDecimal quantity = request.quantity();
        ensureWithinQuota(quantity, quota(workOrder).unclaimedExcluding(line.getQuantity()));
        String lotNumber = support.trimToNull(request.lotNumber());
        if (line.getLot() != null) {
            if (lotNumber != null && !lotNumber.equals(line.getLot().getLotCode())) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.LOT_ITEM_MISMATCH,
                        Map.of("lotNumber", "this draft adds to lot " + line.getLot().getLotCode() + "; it cannot change"));
            }
        } else {
            requireLotWhenTracked(workOrder, null, lotNumber);
            validateNewLotIntent(workOrder, null, lotNumber);
            line.setRequestedLotCode(lotNumber);
        }
        validateDates(workOrder, request.receivedOn(), request.expiresOn(), false);
        idempotencyRecords.record(key, WorkOrderCommand.RECEIPT_UPDATE, receiptId, fingerprint);
        workOrder.touch();
        line.setQuantity(quantity);
        // The quantity lives on the line, an inverse collection: changing it alone never dirties the receipt, so its
        // version would not move and two edits on the same version would both pass (B112, found by the G9a smoke).
        receipt.setUpdatedAt(now());
        receipt.setReceivedOn(request.receivedOn());
        receipt.setExpiresOn(request.expiresOn());
        receipt.setNote(support.trimToNull(request.note()));
        return flush(receipt);
    }

    /**
     * Hands a draft to the approver. Still no inventory impact — this only changes who the
     * document is waiting on.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_SUBMITTED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse submit(UUID workOrderId, UUID receiptId, ProductionReceiptVersionRequest request,
                                            String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(receiptId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.RECEIPT_SUBMIT, fingerprint).isPresent()) {
            return toResponse(findReceipt(receiptId));
        }
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        ensureVersions(receipt, request.expectedVersion(), request.expectedWorkOrderVersion());
        if (!receipt.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a DRAFT receipt can be submitted");
        }
        ensureRequester(receipt, "submit");
        support.ensureReceiptable(receipt.getWorkOrder());
        validateReceiptLotIntent(receipt);
        validateDates(receipt.getWorkOrder(), receipt.getReceivedOn(), receipt.getExpiresOn(), true);
        idempotencyRecords.record(key, WorkOrderCommand.RECEIPT_SUBMIT, receiptId, fingerprint);
        receipt.getWorkOrder().touch();
        receipt.submit(now(), currentActor());
        return flush(receipt);
    }

    /** G9a: the requester takes a pending receipt back to DRAFT to edit it; the output stays claimed (FE R04). */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_WITHDRAWN, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse withdraw(UUID workOrderId, UUID receiptId, ProductionReceiptReasonRequest request,
                                              String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(receiptId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.RECEIPT_WITHDRAW, fingerprint).isPresent()) {
            return toResponse(findReceipt(receiptId));
        }
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        String reason = requiredReason(request, "Withdraw reason is required");
        ensureVersions(receipt, request.expectedVersion(), request.expectedWorkOrderVersion());
        if (!receipt.isPendingApproval()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a receipt pending approval can be withdrawn");
        }
        ensureRequester(receipt, "withdraw");
        idempotencyRecords.record(key, WorkOrderCommand.RECEIPT_WITHDRAW, receiptId, fingerprint);
        receipt.getWorkOrder().touch();
        receipt.withdraw(now(), currentActor(), reason);
        return flush(receipt);
    }

    /**
     * G9a (D8): the requester cancels a DRAFT or pending receipt. Terminal, no stock effect — nothing was posted —
     * and the output it claimed is free again exactly once: a cancelled receipt is no longer open, so it leaves the
     * claim sum (B16). Anyone else is refused; an approver declines with {@code reject}.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_CANCELLED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse cancel(UUID workOrderId, UUID receiptId, ProductionReceiptReasonRequest request,
                                            String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(receiptId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.RECEIPT_CANCEL, fingerprint).isPresent()) {
            return toResponse(findReceipt(receiptId));
        }
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        String reason = requiredReason(request, "Cancel reason is required");
        ensureVersions(receipt, request.expectedVersion(), request.expectedWorkOrderVersion());
        if (!receipt.isOpen()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a DRAFT or pending receipt can be cancelled");
        }
        ensureRequester(receipt, "cancel");
        idempotencyRecords.record(key, WorkOrderCommand.RECEIPT_CANCEL, receiptId, fingerprint);
        receipt.getWorkOrder().touch();
        receipt.cancel(now(), currentActor(), reason);
        return flush(receipt);
    }

    /**
     * Approves a pending receipt: this is where stock is actually created.
     *
     * <p>G9a (FE R08): everything is checked again against the current state, in the same transaction that writes —
     * the versions, the work order still open for receipts, the destination still the work order's active output
     * warehouse, the quota {@code A + q ≤ C}, the lot number still free (ignoring case), the dates. Nothing is
     * corrected silently: a conflict is an error, never another warehouse or another lot.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_APPROVE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_POSTED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse approve(UUID workOrderId, UUID receiptId, ProductionReceiptVersionRequest request,
                                             String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(receiptId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.RECEIPT_APPROVE, fingerprint).isPresent()) {
            return toResponse(findReceipt(receiptId));
        }
        ProductionReceipt receipt = findPendingReceipt(workOrderId, receiptId);
        WorkOrder workOrder = receipt.getWorkOrder();
        ensureVersions(receipt, request.expectedVersion(), request.expectedWorkOrderVersion());
        support.ensureReceiptable(workOrder);
        validateReceiptLotIntent(receipt);
        validateDates(workOrder, receipt.getReceivedOn(), receipt.getExpiresOn(), true);

        BigDecimal alreadyReceipted = workOrder.getCompletedQuantity();
        BigDecimal totalReceived = BigDecimal.ZERO;
        for (ProductionReceiptLine line : receipt.getLines()) {
            ensureOutputWarehouse(workOrder, support.findActiveWarehouseInPlant(
                    line.getWarehouse().getWarehouseId(), workOrder.getPlant()));
            totalReceived = totalReceived.add(line.getQuantity());
        }
        BigDecimal cap = quota(workOrder).cap();
        if (alreadyReceipted.add(totalReceived).compareTo(cap) > 0) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, Map.of("quantity",
                    "approving would put " + alreadyReceipted.add(totalReceived).toPlainString()
                            + " in stock, more than the " + cap.toPlainString() + " the work order may receipt"));
        }
        idempotencyRecords.record(key, WorkOrderCommand.RECEIPT_APPROVE, receiptId, fingerprint);

        // F5 inversion (CLAUDE.md §0.5): approval records that output was warehoused, and nothing
        // more. It does NOT mark the work order in progress and does NOT complete it — only
        // Production Execution can do that. completedQuantity keeps its original meaning,
        // "how much has been receipted", which is exactly what this step advances.
        // G9a: every change to the work order happens before the movements can flush it, and it is touched once
        // (the +2 version trap of G8b, WorkOrderExecutionSupport.flushTouched).
        workOrder.setCompletedQuantity(alreadyReceipted.add(totalReceived));
        workOrder.touch();
        Instant expiresAt = receipt.getExpiresOn() == null ? null
                : receipt.getExpiresOn().atStartOfDay(plantZone(workOrder)).toInstant();
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
                    workOrder.getWorkOrderId().toString(),
                    null,
                    line.getRequestedSerialCode(),
                    line.getLot() == null ? expiresAt : null),
                    idempotency.childKey(receipt.getIdempotencyKey() + ":approve", index),
                    LotStatus.HOLD,
                    false);

            StockMovement movement = movementResult.movement();
            line.setStockMovement(movement);
            line.setLot(movement.getLot());
            line.setSerial(movement.getSerial());
        }

        receipt.approve(now(), currentActor());
        receipt.setSourceWipTraceIds(sourceWipTraceIds(workOrder, alreadyReceipted, totalReceived));

        ProductionReceipt saved = receiptRepository.saveAndFlush(receipt);
        wipTransactionService.recordOutputReceipted(workOrder, totalReceived, saved.getReceiptId());
        support.flushTouched(workOrder);
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
     * Rejects a pending receipt. No inventory movement is created; the output it claimed is free again.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_APPROVE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_REJECTED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse reject(UUID workOrderId,
                                            UUID receiptId,
                                            ProductionReceiptReasonRequest request,
                                            String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(receiptId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.RECEIPT_REJECT, fingerprint).isPresent()) {
            return toResponse(findReceipt(receiptId));
        }
        ProductionReceipt receipt = findPendingReceipt(workOrderId, receiptId);
        String reason = requiredReason(request, "Reject reason is required");
        ensureVersions(receipt, request.expectedVersion(), request.expectedWorkOrderVersion());
        idempotencyRecords.record(key, WorkOrderCommand.RECEIPT_REJECT, receiptId, fingerprint);
        receipt.getWorkOrder().touch();
        receipt.reject(now(), currentActor(), reason);
        return flush(receipt);
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
        if (receipt.getWorkOrder().getProductItem().isLotTracked()
                && receipt.getLines().stream().anyMatch(line -> line.getLot() == null)) {
            throw ExceptionFactory.custom(BusinessErrorCode.OUTPUT_LOT_NOT_POSTED,
                    "QC can only disposition the output lot created or reused at receipt approval");
        }
        String reason = support.trimToNull(request.reason());
        if (reason == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.APPROVAL_REASON_REQUIRED,
                    "QC disposition reason is required");
        }

        Instant now = now();
        UUID actor = currentActor();
        receipt.getWorkOrder().touch();
        // The condition is per line, not a flag on the item: what decides the path is whether
        // approval actually opened a lot (or a serial) to carry the verdict.
        List<ProductionReceiptLine> lotLines = receipt.getLines().stream()
                .filter(line -> line.getLot() != null)
                .toList();
        List<ProductionReceiptLine> serialLines = receipt.getLines().stream()
                .filter(line -> line.getSerial() != null)
                .toList();
        BigDecimal dispositionedQuantity;
        if (!lotLines.isEmpty()) {
            dispositionedQuantity = dispositionLots(receipt, lotLines, request.result(), reason, now, actor);
        } else if (!serialLines.isEmpty()) {
            dispositionedQuantity = dispositionSerials(receipt, serialLines, request.result(), reason);
        } else {
            dispositionedQuantity = dispositionWithoutLots(receipt, request.result());
        }

        // Spec §7.1: fulfilment is driven by QC release and nothing else. REJECTED output stays on
        // hand but unusable, so it must never touch an allocation or a sales order (F6).
        if (request.result() == QualityDispositionResult.AVAILABLE) {
            allocationService.fulfill(receipt.getWorkOrder(), dispositionedQuantity);
        }

        receipt.recordQcDecision(request.result(), reason, now, actor);
        return flush(receipt);
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
     * QC on output that is not lot-tracked. Without a lot, approval records the pending quantity in
     * {@code StockBalance.qualityHoldQuantity}. The verdict is recorded on the <em>receipt</em>
     * ({@code qcResult}/{@code qcReason}/{@code qcAt}/{@code qcBy}) and no
     * {@code quality_dispositions} row is written: that table is one row per lot by definition, and a
     * row without a lot would contradict it. A QC report built from that table therefore does not see
     * these receipts.
     *
     * <p>{@code AVAILABLE} atomically releases that hold without moving on-hand quantity.
     * {@code REJECTED} leaves the quantity on hand and held. In both cases the receipt QC fields are
     * the traceable decision; only AVAILABLE unlocks Sales Order fulfilment.
     */
    private BigDecimal dispositionWithoutLots(ProductionReceipt receipt,
                                              QualityDispositionResult result) {
        BigDecimal dispositionedQuantity = BigDecimal.ZERO;
        for (ProductionReceiptLine line : receipt.getLines()) {
            if (result == QualityDispositionResult.AVAILABLE) {
                movementService.releaseQualityHold(
                        line.getItem().getItemId(),
                        line.getWarehouse().getWarehouseId(),
                        line.getQuantity());
            }
            dispositionedQuantity = dispositionedQuantity.add(line.getQuantity());
        }
        return dispositionedQuantity;
    }

    /**
     * QC on serial-tracked output. Structurally the same as {@link #dispositionWithoutLots} — the
     * unit went straight to free stock at approval (no HOLD gate, see the P5 scope decision in
     * {@code module/workorder/CLAUDE.md}) — plus it additionally records the verdict on the serial's
     * own status for genealogy, even though that status never gated availability.
     */
    private BigDecimal dispositionSerials(ProductionReceipt receipt,
                                          List<ProductionReceiptLine> serialLines,
                                          QualityDispositionResult result,
                                          String reason) {
        BigDecimal dispositionedQuantity = BigDecimal.ZERO;
        int index = 0;
        for (ProductionReceiptLine line : serialLines) {
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
                        receipt.getWorkOrder().getWorkOrderId().toString(),
                        line.getSerial().getSerialId(),
                        null),
                        idempotency.childKey(receipt.getIdempotencyKey() + ":qc-reject", index));
            }
            line.getSerial().setStatus(result == QualityDispositionResult.AVAILABLE
                    ? SerialStatus.AVAILABLE
                    : SerialStatus.REJECTED);
            dispositionedQuantity = dispositionedQuantity.add(line.getQuantity());
        }
        return dispositionedQuantity;
    }

    /** G9a: one receipt of the work order, with its quota, versions and allowed actions. */
    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasAnyWorkOrderAccess(authentication, "
            + "{'PERM_PRODUCTION_RECEIPT_READ', 'PERM_PRODUCTION_RECEIPT_MANAGE', 'PERM_PRODUCTION_RECEIPT_APPROVE', "
            + "'PERM_PRODUCTION_QC_READ', 'PERM_QUALITY_DISPOSITION'}, #workOrderId)")
    public ProductionReceiptResponse get(UUID workOrderId, UUID receiptId) {
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        ensureQcOnlyReadsApproved(receipt);
        return toResponse(receipt);
    }

    /**
     * G9a (D9): the lifecycle of one receipt read from its own columns, oldest first. A receipt withdrawn and
     * submitted again shows its latest submission and withdrawal only; {@code /v1/audit-logs} has every step.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasAnyWorkOrderAccess(authentication, "
            + "{'PERM_PRODUCTION_RECEIPT_READ', 'PERM_PRODUCTION_RECEIPT_MANAGE', 'PERM_PRODUCTION_RECEIPT_APPROVE', "
            + "'PERM_PRODUCTION_QC_READ', 'PERM_QUALITY_DISPOSITION'}, #workOrderId)")
    public PageResult<ProductionReceiptEventResponse> events(UUID workOrderId, UUID receiptId, Pageable pageable) {
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        ensureQcOnlyReadsApproved(receipt);
        List<Event> events = new ArrayList<>();
        events.add(new Event("CREATED", receipt.getCreatedBy(), null, receipt.getCreatedAt()));
        events.add(new Event("SUBMITTED", receipt.getSubmittedBy(), null, receipt.getSubmittedAt()));
        events.add(new Event("WITHDRAWN", receipt.getWithdrawnBy(), receipt.getWithdrawReason(), receipt.getWithdrawnAt()));
        events.add(new Event("APPROVED", receipt.getApprovedBy(), null, receipt.getApprovedAt()));
        events.add(new Event("REJECTED", receipt.getRejectedBy(), receipt.getRejectReason(), receipt.getRejectedAt()));
        events.add(new Event("CANCELLED", receipt.getCancelledBy(), receipt.getCancelReason(), receipt.getCancelledAt()));
        events.add(new Event("QC_DECIDED", receipt.getQcBy(), receipt.getQcReason(), receipt.getQcAt()));
        List<Event> happened = events.stream()
                .filter(event -> event.at() != null)
                .sorted(Comparator.comparing(Event::at))
                .toList();
        Map<UUID, String> usernames = userLookupService.findUsernames(
                happened.stream().map(Event::actor).filter(Objects::nonNull).distinct().toList());
        List<ProductionReceiptEventResponse> rows = happened.stream()
                .map(event -> new ProductionReceiptEventResponse(event.action(), event.actor(),
                        event.actor() == null ? null : usernames.get(event.actor()), event.reason(), event.at()))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return PageResult.from(new PageImpl<>(rows.subList(from, to), pageable, rows.size()));
    }

    private record Event(String action, UUID actor, String reason, Instant at) {}

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasAnyWorkOrderAccess(authentication, "
            + "{'PERM_PRODUCTION_RECEIPT_READ', 'PERM_PRODUCTION_RECEIPT_MANAGE', 'PERM_PRODUCTION_RECEIPT_APPROVE', "
            + "'PERM_PRODUCTION_QC_READ', 'PERM_QUALITY_DISPOSITION'}, #workOrderId)")
    public PageResult<ProductionReceiptResponse> list(UUID workOrderId, Pageable pageable) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        UUID plantId = workOrder.getPlant().getPlantId();
        if (!canReadEveryReceipt(plantId)) {
            return toPage(receiptRepository.findByPlant(plantId, ProductionReceiptStatus.APPROVED, workOrderId,
                    null, null, null, null, pageable));
        }
        return toPage(receiptRepository.findByWorkOrderWorkOrderId(workOrderId, pageable));
    }

    /**
     * Flat plant-scoped list (spec §6.2) with the G9a filters (FE §3.3).
     *
     * <p>G9a (D6): readable with {@code PERM_PRODUCTION_RECEIPT_READ} or with an action permission on receipts
     * ({@code MANAGE}, {@code APPROVE}) — nobody who reads receipts today loses that. QC alone
     * ({@code PERM_PRODUCTION_QC_READ}, {@code PERM_QUALITY_DISPOSITION}) reads APPROVED receipts only (FE §1.3):
     * a request for another status answers an empty page rather than a 403, so the QC screen can share the list.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_RECEIPT_READ', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_RECEIPT_APPROVE', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_QC_READ', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_QUALITY_DISPOSITION', 'PLANT', #plantId)")
    public PageResult<ProductionReceiptResponse> listByPlant(UUID plantId,
                                                             ProductionReceiptStatus status,
                                                             UUID workOrderId,
                                                             UUID destinationWarehouseId,
                                                             LocalDate receivedFrom,
                                                             LocalDate receivedTo,
                                                             String search,
                                                             Pageable pageable) {
        if (receivedFrom != null && receivedTo != null && receivedFrom.isAfter(receivedTo)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("receivedFrom", "must not be after receivedTo"));
        }
        ProductionReceiptStatus effectiveStatus = status;
        if (!canReadEveryReceipt(plantId)) {
            if (status != null && status != ProductionReceiptStatus.APPROVED) {
                return PageResult.from(Page.empty(pageable));
            }
            effectiveStatus = ProductionReceiptStatus.APPROVED;
        }
        return toPage(receiptRepository.findByPlant(plantId, effectiveStatus, workOrderId, destinationWarehouseId,
                receivedFrom, receivedTo, support.trimToNull(search), pageable));
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
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_RECEIPT_READ', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_RECEIPT_APPROVE', 'PLANT', #plantId)")
    public PageResult<ProductionReceiptCandidateResponse> listCandidates(UUID plantId, UUID workOrderId, String search,
                                                                         Pageable pageable) {
        Page<WorkOrder> page = workOrderRepository.findReceiptCandidates(
                plantId, RECEIPT_CANDIDATE_STATUSES, workOrderId, support.trimToNull(search), pageable);
        Map<UUID, BigDecimal> openByWorkOrder = openClaims(page.getContent());
        return PageResult.from(page.map(workOrder -> mapper.toReceiptCandidateResponse(
                workOrder,
                openByWorkOrder.getOrDefault(workOrder.getWorkOrderId(), BigDecimal.ZERO))));
    }

    /** Loads the lines, author names, claims and permissions of a page of receipts in one batch each (rule C14). */
    private PageResult<ProductionReceiptResponse> toPage(Page<ProductionReceipt> page) {
        List<UUID> receiptIds = page.getContent().stream()
                .map(ProductionReceipt::getReceiptId)
                .toList();
        Map<UUID, List<ProductionReceiptLine>> linesByReceipt = receiptIds.isEmpty()
                ? Map.of()
                : receiptLineRepository.findByReceiptReceiptIdIn(receiptIds).stream()
                .collect(Collectors.groupingBy(line -> line.getReceipt().getReceiptId()));
        Map<UUID, String> usernames = usernamesOf(page.getContent());
        Map<UUID, BigDecimal> claims = openClaims(page.getContent().stream().map(ProductionReceipt::getWorkOrder).toList());
        Map<UUID, Set<String>> granted = new HashMap<>();
        UUID actor = currentActor();
        return PageResult.from(page.map(receipt -> {
            List<ProductionReceiptLine> lines = linesByReceipt.getOrDefault(receipt.getReceiptId(), List.of());
            WorkOrder workOrder = receipt.getWorkOrder();
            Set<String> permissions = granted.computeIfAbsent(workOrder.getPlant().getPlantId(), this::grantedPermissions);
            return mapper.toResponse(receipt, lines, usernames,
                    ReceiptQuota.of(workOrder, claims.getOrDefault(workOrder.getWorkOrderId(), BigDecimal.ZERO)),
                    allowedActions(receipt, lines, permissions, actor));
        }));
    }

    /**
     * Creates the receipt in {@code DRAFT}. Deliberately does not create any stock movement, does
     * not increase {@code completedQuantity} and does not record WIP — those all happen in
     * {@link #approve}.
     */
    private ProductionReceiptResponse postNew(UUID workOrderId,
                                              ProductionReceiptPostRequest request,
                                              String normalizedKey,
                                              boolean versioned) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        LocalDate receivedOn = request.receivedOn();
        if (versioned) {
            WorkOrderVersions.ensure(workOrder, request.expectedWorkOrderVersion(), "expectedWorkOrderVersion");
            if (receivedOn == null) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                        Map.of("receivedOn", "must not be null"));
            }
        } else if (receivedOn == null) {
            receivedOn = LocalDate.now(plantZone(workOrder));
        }
        support.ensureReceiptable(workOrder);

        Warehouse warehouse = support.findActiveWarehouseInPlant(
                request.destinationWarehouseId(), workOrder.getPlant());
        ensureOutputWarehouse(workOrder, warehouse);
        BigDecimal quantity = support.requirePositive(request.quantity(), "Receipt quantity");

        // Invariant B16, rewritten in F5 and clamped in G9a (D12): the ceiling is what the shop floor actually
        // produced, up to the plan, and has not been receipted yet. Receipts that are still open (DRAFT /
        // PENDING_APPROVAL) have already claimed part of it.
        ensureWithinQuota(quantity, quota(workOrder).unclaimed());
        // Fail here rather than at approval: without a lot the output can never leave HOLD,
        // so a lot-tracked receipt with no lot would be un-QC-able (spec §6.3).
        requireLotWhenTracked(workOrder, request.lotId(), request.lotNumber());
        validateNewLotIntent(workOrder, request.lotId(), request.lotNumber());
        validateDates(workOrder, receivedOn, request.expiresOn(), false);
        // Symmetric to the lot check above (B41 sibling): a serial-tracked receipt always names
        // exactly one physical unit, so quantity must be 1 and the serial code is mandatory.
        if (workOrder.getProductItem().isSerialTracked()) {
            if (support.trimToNull(request.serialNumber()) == null) {
                throw ExceptionFactory.custom(ValidationErrorCode.SERIAL_REQUIRED,
                        "Serial-tracked output requires a serial number");
            }
            if (quantity.compareTo(BigDecimal.ONE) != 0) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Serial-tracked output must be receipted one unit at a time");
            }
        }

        ProductionReceipt receipt = ProductionReceipt.builder()
                .workOrder(workOrder)
                .status(ProductionReceiptStatus.DRAFT)
                .code(generateCode())
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .traceId(traceIdProvider.currentTraceId())
                .note(support.trimToNull(request.note()))
                .receivedOn(receivedOn)
                .expiresOn(request.expiresOn())
                .build();
        receipt.getLines().add(ProductionReceiptLine.builder()
                .receipt(receipt)
                .item(workOrder.getProductItem())
                .warehouse(warehouse)
                .lot(request.lotId() != null
                        ? itemLookupService.getLotForItem(workOrder.getProductItem(), request.lotId())
                        : null)
                .requestedLotCode(support.trimToNull(request.lotNumber()))
                .requestedSerialCode(support.trimToNull(request.serialNumber()))
                .quantity(quantity)
                .reason(support.trimToNull(request.reason()))
                .build());
        // The receipt claims output of the work order, so the work order's version moves on (FE Q12).
        workOrder.touch();
        return flush(receipt);
    }

    /** Writes the receipt and the touched work order, then answers with both versions as written (§0.39). */
    private ProductionReceiptResponse flush(ProductionReceipt receipt) {
        ProductionReceipt saved = receiptRepository.saveAndFlush(receipt);
        support.flushTouched(saved.getWorkOrder());
        return toResponse(saved);
    }

    private ReceiptQuota quota(WorkOrder workOrder) {
        return ReceiptQuota.of(workOrder, receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()));
    }

    private static void ensureWithinQuota(BigDecimal quantity, BigDecimal unclaimed) {
        if (quantity.compareTo(unclaimed) > 0) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, Map.of("quantity",
                    "exceeds the " + unclaimed.toPlainString()
                            + " of final good, up to the plan, that no receipt has claimed yet"));
        }
    }

    /** FE R02: a receipt puts output in the work order's own output warehouse, never another one. */
    private static void ensureOutputWarehouse(WorkOrder workOrder, Warehouse warehouse) {
        if (!workOrder.getOutputWarehouse().getWarehouseId().equals(warehouse.getWarehouseId())) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH, Map.of(
                    "destinationWarehouseId", "must be the work order's output warehouse "
                            + workOrder.getOutputWarehouse().getCode()));
        }
    }

    /**
     * G9a (FE R21, Q23): {@code receivedOn} is never after the plant's today; {@code expiresOn} is only for
     * lot-tracked output, after {@code receivedOn} and after today. On create/edit a bad date is the input's
     * fault (400); on submit/approve the same date was fine when saved and time moved on, so it is a 409.
     */
    private void validateDates(WorkOrder workOrder, LocalDate receivedOn, LocalDate expiresOn, boolean recheck) {
        LocalDate today = LocalDate.now(plantZone(workOrder));
        Map<String, String> errors = new LinkedHashMap<>();
        if (receivedOn != null && receivedOn.isAfter(today)) {
            errors.put("receivedOn", "must not be after the plant's today " + today);
        }
        if (expiresOn != null) {
            if (!workOrder.getProductItem().isLotTracked()) {
                errors.put("expiresOn", "only lot-tracked output carries an expiry");
            } else if (receivedOn != null && !expiresOn.isAfter(receivedOn)) {
                errors.put("expiresOn", "must be after receivedOn");
            } else if (!expiresOn.isAfter(today)) {
                errors.put("expiresOn", "must be after the plant's today " + today);
            }
        }
        if (!errors.isEmpty()) {
            if (recheck && errors.containsKey("expiresOn") && workOrder.getProductItem().isLotTracked()) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.LOT_NOT_ELIGIBLE, errors);
            }
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
    }

    private static ZoneId plantZone(WorkOrder workOrder) {
        return ZoneId.of(workOrder.getPlant().getTimezone());
    }

    private void requireLotWhenTracked(WorkOrder workOrder, UUID lotId, String lotNumber) {
        if (workOrder.getProductItem().isLotTracked()
                && lotId == null
                && support.trimToNull(lotNumber) == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.LOT_REQUIRED,
                    "Lot-tracked output requires a lot number or lot id");
        }
    }

    private void validateReceiptLotIntent(ProductionReceipt receipt) {
        if (!receipt.getWorkOrder().getProductItem().isLotTracked()) {
            return;
        }
        for (ProductionReceiptLine line : receipt.getLines()) {
            validateNewLotIntent(receipt.getWorkOrder(),
                    line.getLot() == null ? null : line.getLot().getLotId(),
                    line.getRequestedLotCode());
        }
    }

    /**
     * Lot codes are trimmed. Supplying a code means create-new and therefore rejects a code any lot of the item
     * already uses, ignoring case (G9a, FE Q8 — the same rule as purchase child lots, B205); supplying lotId is
     * the only explicit partial-reuse contract. Drafts do not reserve their codes (D11): approval checks again.
     */
    private void validateNewLotIntent(WorkOrder workOrder, UUID lotId, String lotCode) {
        if (!workOrder.getProductItem().isLotTracked()) {
            return;
        }
        String normalizedCode = support.trimToNull(lotCode);
        if (lotId != null) {
            var lot = itemLookupService.getLotForItem(workOrder.getProductItem(), lotId);
            if (normalizedCode != null && !normalizedCode.equals(lot.getLotCode())) {
                throw ExceptionFactory.custom(BusinessErrorCode.LOT_ITEM_MISMATCH,
                        "lotId and lotNumber identify different lots");
            }
            if (lot.getStatus() != LotStatus.HOLD) {
                throw ExceptionFactory.custom(BusinessErrorCode.LOT_REUSE_NOT_ALLOWED,
                        "Only a HOLD lot can be reused by a partial production receipt");
            }
            return;
        }
        if (normalizedCode != null
                && itemLookupService.lotCodeExistsIgnoreCase(workOrder.getProductItem().getItemId(), normalizedCode)) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.LOT_CODE_ALREADY_EXISTS, Map.of("lotNumber",
                    "lot " + normalizedCode + " already exists for this item; send its lotId only for an eligible partial receipt"));
        }
    }

    /** G9a (FE R06, D8): editing, submitting, withdrawing and cancelling belong to the requester alone. */
    private void ensureRequester(ProductionReceipt receipt, String action) {
        UUID actor = currentActor();
        if (actor == null || !actor.equals(receipt.getCreatedBy())) {
            throw ExceptionFactory.custom(AuthErrorCode.ACCESS_DENIED,
                    "Only the person who created the receipt can " + action + " it");
        }
    }

    /** Both versions the client last read (FE R19): the receipt's and the work order's. */
    private static void ensureVersions(ProductionReceipt receipt, Long expectedVersion, Long expectedWorkOrderVersion) {
        WorkOrderVersions.ensure(receipt.getWorkOrder(), expectedWorkOrderVersion, "expectedWorkOrderVersion");
        if (expectedVersion == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("expectedVersion", "must not be null"));
        }
        if (!Objects.equals(receipt.getVersion(), expectedVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION, "Production receipt version is "
                    + receipt.getVersion() + ", not " + expectedVersion + "; reload it");
        }
    }

    /** Missing ⇒ the specific {@code APPROVAL_REASON_REQUIRED}; present but outside 5–2000 ⇒ field error (FE Q25). */
    private static String requiredReason(ProductionReceiptReasonRequest request, String missingMessage) {
        String reason = ReasonRules.lengthChecked(request.reason(), "reason");
        if (reason == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.APPROVAL_REASON_REQUIRED, missingMessage);
        }
        return reason;
    }

    /** FE §1.3: QC alone reads approved receipts only. */
    private void ensureQcOnlyReadsApproved(ProductionReceipt receipt) {
        if (!receipt.isApproved() && !canReadEveryReceipt(receipt.getWorkOrder().getPlant().getPlantId())) {
            throw ExceptionFactory.custom(AuthErrorCode.ACCESS_DENIED,
                    "Quality permissions read approved production receipts only");
        }
    }

    private boolean canReadEveryReceipt(UUID plantId) {
        Set<String> granted = grantedPermissions(plantId);
        return granted.contains(PERM_READ) || granted.contains(PERM_MANAGE) || granted.contains(PERM_APPROVE);
    }

    private Set<String> grantedPermissions(UUID plantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> granted = new HashSet<>();
        for (String permission : PERMISSIONS) {
            if (permissionGuard.hasResourceAccess(authentication, permission, "PLANT", plantId)) {
                granted.add(permission);
            }
        }
        return granted;
    }

    /**
     * What the caller may do on the receipt now (FE §3.2 {@code allowedActions}). A convenience for the buttons:
     * every command still checks everything itself.
     */
    private static List<String> allowedActions(ProductionReceipt receipt, List<ProductionReceiptLine> lines,
                                               Set<String> permissions, UUID actor) {
        List<String> actions = new ArrayList<>();
        boolean requester = actor != null && actor.equals(receipt.getCreatedBy());
        boolean receiptable = receipt.getWorkOrder().canReceipt();
        boolean manage = requester && permissions.contains(PERM_MANAGE);
        switch (receipt.getStatus()) {
            case DRAFT -> {
                if (manage && receiptable) {
                    actions.add("EDIT");
                    actions.add("SUBMIT");
                }
                if (manage) {
                    actions.add("CANCEL");
                }
            }
            case PENDING_APPROVAL -> {
                if (manage) {
                    actions.add("WITHDRAW");
                    actions.add("CANCEL");
                }
                if (permissions.contains(PERM_APPROVE)) {
                    if (receiptable) {
                        actions.add("APPROVE");
                    }
                    actions.add("REJECT");
                }
            }
            case APPROVED -> {
                boolean serial = lines.stream().anyMatch(line -> line.getSerial() != null);
                if (permissions.contains(PERM_QC) && !receipt.isQcDecided() && !serial) {
                    actions.add("QC");
                }
            }
            default -> {
            }
        }
        return actions;
    }

    private Map<UUID, BigDecimal> openClaims(List<WorkOrder> workOrders) {
        List<UUID> workOrderIds = workOrders.stream().map(WorkOrder::getWorkOrderId).distinct().toList();
        return workOrderIds.isEmpty()
                ? Map.of()
                : receiptRepository.sumOpenQuantityByWorkOrderIds(workOrderIds).stream()
                .collect(Collectors.toMap(
                        OpenReceiptQuantityProjection::getWorkOrderId,
                        OpenReceiptQuantityProjection::getOpenQuantity));
    }

    /** One receipt with its quota, usernames and the caller's allowed actions. */
    private ProductionReceiptResponse toResponse(ProductionReceipt receipt) {
        WorkOrder workOrder = receipt.getWorkOrder();
        Set<String> permissions = grantedPermissions(workOrder.getPlant().getPlantId());
        return mapper.toResponse(receipt, receipt.getLines(), usernamesOf(List.of(receipt)), quota(workOrder),
                allowedActions(receipt, receipt.getLines(), permissions, currentActor()));
    }

    private Map<UUID, String> usernamesOf(List<ProductionReceipt> receipts) {
        List<UUID> actorIds = receipts.stream()
                .flatMap(receipt -> Stream.of(receipt.getCreatedBy(), receipt.getApprovedBy(), receipt.getQcBy(),
                        receipt.getSubmittedBy(), receipt.getRejectedBy(), receipt.getWithdrawnBy(),
                        receipt.getCancelledBy()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return userLookupService.findUsernames(actorIds);
    }

    /**
     * Lifecycle instants cut to microseconds, what Postgres keeps: a command must answer the instant a later read
     * returns (the 100 ns clock of Windows, CLAUDE.md §0.51 #3 — found again by the G9a smoke on postedAt/approvedAt).
     */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private UUID currentActor() {
        return auditorAware.getCurrentAuditor().orElse(null);
    }

    private static ProductionReceiptLine singleLine(ProductionReceipt receipt) {
        if (receipt.getLines().size() != 1) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "This receipt predates single-line receipts and cannot be edited");
        }
        return receipt.getLines().get(0);
    }

    private ProductionReceipt findPendingReceipt(UUID workOrderId, UUID receiptId) {
        ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
        if (!receipt.isPendingApproval()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only receipts pending approval can be approved or rejected");
        }
        return receipt;
    }

    private ProductionReceipt findReceipt(UUID receiptId) {
        return receiptRepository.findWithLinesByReceiptId(receiptId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Production receipt", receiptId));
    }

    private ProductionReceipt findReceiptOfWorkOrder(UUID workOrderId, UUID receiptId) {
        ProductionReceipt receipt = findReceipt(receiptId);
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
