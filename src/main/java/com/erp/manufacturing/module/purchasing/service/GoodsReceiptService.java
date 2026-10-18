package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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

@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 120;

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryMovementService inventoryMovementService;
    private final PurchasingMapper mapper;
    private final IdempotencySupport idempotency;
    private final GoodsReceiptPoster poster;
    private final PurchasingPermissionGuard purchasingPermissionGuard;
    private final AuditorAware<UUID> auditorAware;
    private final PurchasingIdempotency idempotencyRecords;

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_GOODS_RECEIPT_POST', #purchaseOrderId)")
    @Auditable(action = AuditAction.GOODS_RECEIPT_POSTED, entityType = "GoodsReceipt", entityIdExpression = "goodsReceiptId.toString()",
               warehouseId = "#result?.warehouseId()")
    public GoodsReceiptResponse post(UUID purchaseOrderId, GoodsReceiptPostRequest request, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<GoodsReceipt> existing = goodsReceiptRepository
                .findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(purchaseOrderId, normalizedKey);
        if (existing.isPresent()) {
            idempotency.ensureSamePayload(existing.get().getPayloadHash(), request);
            return mapper.toResponse(existing.get(), true);
        }

        // G7b (D11): lock the order before its lines are read, so concurrent receipts against the
        // same open quantity serialise instead of both passing the check.
        PurchaseOrder order = poster.lockOrder(purchaseOrderId);
        order = findOrder(purchaseOrderId);
        if (!order.canReceive()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only CONFIRMED or PARTIALLY_RECEIVED purchase orders can receive goods");
        }
        Map<UUID, PurchaseOrderLine> orderLines = order.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getPurchaseOrderLineId, line -> line));
        List<GoodsReceiptPoster.Line> lines = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            GoodsReceiptLineRequest lineRequest = request.lines().get(i);
            PurchaseOrderLine orderLine = orderLines.get(lineRequest.purchaseOrderLineId());
            if (orderLine == null) {
                throw ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase order line", lineRequest.purchaseOrderLineId());
            }
            lines.add(new GoodsReceiptPoster.Line(i, orderLine,
                    requirePositive(lineRequest.receivedQuantity(), "Received quantity"),
                    lineRequest.lotId(), lineRequest.lotCode(), null));
        }
        LocalDate today = LocalDate.now(ZoneId.of(order.getPlant().getTimezone()));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        GoodsReceipt receipt = goodsReceiptRepository.save(GoodsReceipt.builder()
                .purchaseOrder(order)
                .warehouse(order.getWarehouse())
                .receiptNo(normalizeCode(request.receiptNo(), "Goods receipt number"))
                .status(GoodsReceiptStatus.DRAFT)
                .receivedDate(today)
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .note(trimToNull(request.note()))
                .build());

        // D4: the legacy path now also receives on hold — one rule for both, no second number.
        List<InventoryMovementResult> results = poster.post(order, lines, false, normalizedKey, request.note(),
                receipt.getGoodsReceiptId().toString(), today, now);
        for (int i = 0; i < lines.size(); i++) {
            StockMovement movement = results.get(i).movement();
            receipt.getLines().add(GoodsReceiptLine.builder()
                    .goodsReceipt(receipt)
                    .purchaseOrderLine(lines.get(i).orderLine())
                    .item(lines.get(i).orderLine().getItem())
                    .lot(movement.getLot())
                    .lotCode(movement.getLot() == null ? null : movement.getLot().getLotCode())
                    .receivedQuantity(lines.get(i).quantity())
                    .stockMovement(movement)
                    .build());
        }
        receipt.markPosted(now, auditorAware.getCurrentAuditor().orElse(null));
        // saveAndFlush: the response carries version (G7b), which Hibernate only bumps at flush (§0.39).
        purchaseOrderRepository.saveAndFlush(order);
        return mapper.toResponse(goodsReceiptRepository.saveAndFlush(receipt), true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_READ', #purchaseOrderId)")
    public PageResult<GoodsReceiptResponse> list(UUID purchaseOrderId, Pageable pageable) {
        return PageResult.from(goodsReceiptRepository.findByPurchaseOrderPurchaseOrderId(purchaseOrderId, pageable)
                .map(receipt -> mapper.toResponse(receipt, false)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_GOODS_RECEIPT_READ', #goodsReceiptId)"
            + " or @purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_PURCHASE_ORDER_READ', #goodsReceiptId)")
    public GoodsReceiptResponse get(UUID goodsReceiptId) {
        return mapper.toResponse(goodsReceiptRepository.findWithDetailsByGoodsReceiptId(goodsReceiptId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Goods receipt", goodsReceiptId)), true);
    }

    /**
     * Cancels a goods receipt — one endpoint, two meanings by status (G7b, D3):
     * <ul>
     *   <li>DRAFT: becomes CANCELLED; no stock ever moved, nothing to reverse. Needs
     *       {@code PERM_GOODS_RECEIPT_MANAGE} or {@code PERM_PURCHASE_ORDER_MANAGE}.</li>
     *   <li>POSTED (legacy): a REVERSAL movement per line, received quantities and PO status roll back.
     *       Still needs {@code PERM_PURCHASE_ORDER_MANAGE} — checked here once the status is known.
     *       A receipt posted on hold first releases the quality hold it created for stock without a
     *       lot, so on-hand never drops below the held quantity.</li>
     * </ul>
     * {@code expectedVersion} is optional (legacy callers send none).
     */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #goodsReceiptId)"
            + " or @purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_GOODS_RECEIPT_MANAGE', #goodsReceiptId)")
    @Auditable(action = AuditAction.GOODS_RECEIPT_CANCELLED, entityType = "GoodsReceipt",
            entityIdExpression = "#goodsReceiptId.toString()",
               warehouseId = "#result?.warehouseId()")
    public GoodsReceiptResponse cancel(UUID goodsReceiptId, GoodsReceiptCancelRequest request) {
        return cancel(goodsReceiptId, request, null);
    }

    /** As {@link #cancel(UUID, GoodsReceiptCancelRequest)}, with an optional {@code Idempotency-Key} (G7b). */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #goodsReceiptId)"
            + " or @purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_GOODS_RECEIPT_MANAGE', #goodsReceiptId)")
    @Auditable(action = AuditAction.GOODS_RECEIPT_CANCELLED, entityType = "GoodsReceipt",
            entityIdExpression = "#goodsReceiptId.toString()",
               warehouseId = "#result?.warehouseId()")
    public GoodsReceiptResponse cancel(UUID goodsReceiptId, GoodsReceiptCancelRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        PurchasingIdempotency.Fingerprint fingerprint = new PurchasingIdempotency.Fingerprint(goodsReceiptId, request);
        GoodsReceipt receipt = goodsReceiptRepository.findWithDetailsByGoodsReceiptId(goodsReceiptId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Goods receipt", goodsReceiptId));
        if (idempotencyRecords.replay(key, PurchasingCommand.GOODS_RECEIPT_CANCEL, fingerprint).isPresent()) {
            return mapper.toResponse(receipt, true);
        }
        if (request != null && request.expectedVersion() != null
                && !Objects.equals(request.expectedVersion(), receipt.getVersion())) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Goods receipt version is " + receipt.getVersion() + ", not " + request.expectedVersion() + "; reload it");
        }
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        if (receipt.isDraft() || receipt.isPosted()) {
            idempotencyRecords.record(key, PurchasingCommand.GOODS_RECEIPT_CANCEL, goodsReceiptId, fingerprint);
        }
        if (receipt.isDraft()) {
            receipt.cancel(request == null ? null : trimToNull(request.cancelNote()),
                    Instant.now().truncatedTo(ChronoUnit.MICROS), actor);
            return mapper.toResponse(goodsReceiptRepository.saveAndFlush(receipt), true);
        }
        if (receipt.isPosted() && !purchasingPermissionGuard.hasReceiptAccess(
                SecurityContextHolder.getContext().getAuthentication(), "PERM_PURCHASE_ORDER_MANAGE", goodsReceiptId)) {
            throw new AccessDeniedException("Reversing a posted goods receipt needs PERM_PURCHASE_ORDER_MANAGE");
        }

        if (!receipt.isPosted()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only POSTED goods receipts can be cancelled");
        }

        PurchaseOrder order = receipt.getPurchaseOrder();
        // G7a: a CLOSED order gave up its remainder. Reversing a receipt would hand open quantity
        // back to an order that can no longer receive it, so the reversal is refused instead.
        if (order.getStatus() == PurchaseOrderStatus.CLOSED) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Goods receipts of a CLOSED purchase order cannot be cancelled");
        }
        // Re-load PO with lines so we can decrement receivedQuantity
        PurchaseOrder orderWithLines = purchaseOrderRepository.findWithDetailsByPurchaseOrderId(
                order.getPurchaseOrderId())
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase order", order.getPurchaseOrderId()));

        Map<UUID, PurchaseOrderLine> poLineMap = orderWithLines.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getPurchaseOrderLineId, l -> l));

        String cancelIdempotencyBase = goodsReceiptId.toString() + ":cancel";

        int lineIndex = 0;
        for (GoodsReceiptLine line : receipt.getLines()) {
            lineIndex++;
            String reversalKey = buildCancelLineIdempotencyKey(cancelIdempotencyBase, lineIndex);

            UUID warehouseId = receipt.getWarehouse().getWarehouseId();
            UUID lotId = line.getLot() == null ? null : line.getLot().getLotId();

            if (receipt.isQualityHeld() && line.getLot() == null) {
                inventoryMovementService.releaseQualityHold(line.getItem().getItemId(), warehouseId,
                        line.getReceivedQuantity());
            }
            inventoryMovementService.reverseReceive(
                    line.getItem().getItemId(),
                    warehouseId,
                    lotId,
                    line.getReceivedQuantity(),
                    "GOODS_RECEIPT_CANCEL",
                    goodsReceiptId.toString(),
                    reversalKey);

            PurchaseOrderLine poLine = poLineMap.get(line.getPurchaseOrderLine().getPurchaseOrderLineId());
            if (poLine != null) {
                BigDecimal newReceived = poLine.getReceivedQuantity().subtract(line.getReceivedQuantity());
                poLine.setReceivedQuantity(newReceived.max(BigDecimal.ZERO));
            }
        }

        // Refresh PO status after rolling back quantities (FULLY_RECEIVED → PARTIALLY_RECEIVED → CONFIRMED)
        orderWithLines.refreshReceiptStatus();
        purchaseOrderRepository.saveAndFlush(orderWithLines);

        receipt.cancel(request == null ? null : request.cancelNote(),
                Instant.now().truncatedTo(ChronoUnit.MICROS), actor);
        return mapper.toResponse(goodsReceiptRepository.saveAndFlush(receipt), true);
    }

    private String buildCancelLineIdempotencyKey(String base, int lineIndex) {
        String suffix = ":L" + lineIndex;
        if (base.length() + suffix.length() <= IDEMPOTENCY_KEY_MAX_LENGTH) {
            return base + suffix;
        }
        return base.substring(0, IDEMPOTENCY_KEY_MAX_LENGTH - suffix.length()) + suffix;
    }

    private PurchaseOrder findOrder(UUID purchaseOrderId) {
        return purchaseOrderRepository.findWithDetailsByPurchaseOrderId(purchaseOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase order", purchaseOrderId));
    }

    private BigDecimal requirePositive(BigDecimal quantity, String fieldName) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " must be greater than zero");
        }
        return quantity;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        return idempotency.normalizeKey(idempotencyKey);
    }

    private String normalizeCode(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
