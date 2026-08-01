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
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 120;
    private static final String REFERENCE_TYPE_GOODS_RECEIPT = "GOODS_RECEIPT";

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryMovementService inventoryMovementService;
    private final PurchasingMapper mapper;
    private final IdempotencySupport idempotency;

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_GOODS_RECEIPT_POST', #purchaseOrderId)")
    @Auditable(action = AuditAction.GOODS_RECEIPT_POSTED, entityType = "GoodsReceipt", entityIdExpression = "goodsReceiptId.toString()")
    public GoodsReceiptResponse post(UUID purchaseOrderId, GoodsReceiptPostRequest request, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<GoodsReceipt> existing = goodsReceiptRepository
                .findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(purchaseOrderId, normalizedKey);
        if (existing.isPresent()) {
            idempotency.ensureSamePayload(existing.get().getPayloadHash(), request);
            return mapper.toResponse(existing.get(), true);
        }

        PurchaseOrder order = findOrder(purchaseOrderId);
        if (!order.canReceive()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only SENT or PARTIALLY_RECEIVED purchase orders can receive goods");
        }
        Map<UUID, PurchaseOrderLine> orderLines = order.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getPurchaseOrderLineId, line -> line));
        GoodsReceipt receipt = GoodsReceipt.builder()
                .purchaseOrder(order)
                .warehouse(order.getWarehouse())
                .receiptNo(normalizeCode(request.receiptNo(), "Goods receipt number"))
                .status(GoodsReceiptStatus.POSTED)
                .postedAt(Instant.now())
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .note(trimToNull(request.note()))
                .build();
        receipt = goodsReceiptRepository.save(receipt);

        int lineIndex = 0;
        for (GoodsReceiptLineRequest lineRequest : request.lines()) {
            lineIndex++;
            PurchaseOrderLine orderLine = orderLines.get(lineRequest.purchaseOrderLineId());
            if (orderLine == null) {
                throw ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase order line", lineRequest.purchaseOrderLineId());
            }
            BigDecimal receivedQuantity = requirePositive(lineRequest.receivedQuantity(), "Received quantity");
            if (receivedQuantity.compareTo(orderLine.remainingQuantity()) > 0) {
                // B27 – over-receipt is a quantity-vs-document conflict (409), the purchasing analog
                // of ProductionReceiptService's PLANNED_QUANTITY_EXCEEDED. Retagged in D7.
                throw ExceptionFactory.custom(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                        "Received quantity cannot exceed remaining ordered quantity");
            }

            InventoryMovementResult movementResult = inventoryMovementService.receive(
                    new InventoryReceiveCommand(
                            orderLine.getItem().getItemId(),
                            order.getWarehouse().getWarehouseId(),
                            lineRequest.lotId(),
                            lineRequest.lotCode(),
                            receivedQuantity,
                            request.note(),
                            REFERENCE_TYPE_GOODS_RECEIPT,
                            receipt.getGoodsReceiptId().toString()),
                    childIdempotencyKey(normalizedKey, lineIndex));
            StockMovement movement = movementResult.movement();
            orderLine.receive(receivedQuantity);
            GoodsReceiptLine receiptLine = GoodsReceiptLine.builder()
                    .goodsReceipt(receipt)
                    .purchaseOrderLine(orderLine)
                    .item(orderLine.getItem())
                    .lot(movement.getLot())
                    .lotCode(movement.getLot() == null ? null : movement.getLot().getLotCode())
                    .receivedQuantity(receivedQuantity)
                    .stockMovement(movement)
                    .build();
            receipt.getLines().add(receiptLine);
        }

        order.refreshReceiptStatus();
        purchaseOrderRepository.save(order);
        return mapper.toResponse(goodsReceiptRepository.save(receipt), true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_READ', #purchaseOrderId)")
    public PageResult<GoodsReceiptResponse> list(UUID purchaseOrderId, Pageable pageable) {
        return PageResult.from(goodsReceiptRepository.findByPurchaseOrderPurchaseOrderId(purchaseOrderId, pageable)
                .map(receipt -> mapper.toResponse(receipt, false)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_PURCHASE_ORDER_READ', #goodsReceiptId)")
    public GoodsReceiptResponse get(UUID goodsReceiptId) {
        return mapper.toResponse(goodsReceiptRepository.findWithDetailsByGoodsReceiptId(goodsReceiptId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Goods receipt", goodsReceiptId)), true);
    }

    /**
     * Cancels a POSTED goods receipt.
     * Creates a REVERSAL movement for every receipt line to restore stock balance.
     * Decrements receivedQuantity on each PurchaseOrderLine.
     * Refreshes PO status (RECEIVED → PARTIALLY_RECEIVED / SENT if quantities roll back).
     */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #goodsReceiptId)")
    @Auditable(action = AuditAction.GOODS_RECEIPT_CANCELLED, entityType = "GoodsReceipt",
            entityIdExpression = "#goodsReceiptId.toString()")
    public GoodsReceiptResponse cancel(UUID goodsReceiptId, GoodsReceiptCancelRequest request) {
        GoodsReceipt receipt = goodsReceiptRepository.findWithDetailsByGoodsReceiptId(goodsReceiptId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Goods receipt", goodsReceiptId));

        if (!receipt.isPosted()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only POSTED goods receipts can be cancelled");
        }

        PurchaseOrder order = receipt.getPurchaseOrder();
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

        // Refresh PO status after rolling back quantities
        refreshCancelledPOStatus(orderWithLines);
        purchaseOrderRepository.save(orderWithLines);

        receipt.cancel(request == null ? null : request.cancelNote());
        return mapper.toResponse(goodsReceiptRepository.save(receipt), true);
    }

    /**
     * Re-calculates PO status after a goods receipt cancellation.
     * If all lines back to 0 → SENT (was at minimum SENT before any receipt).
     * If some received → PARTIALLY_RECEIVED.
     * If all fully received → RECEIVED (edge case: another receipt still covers it).
     */
    private void refreshCancelledPOStatus(PurchaseOrder order) {
        boolean anyReceived = order.getLines().stream()
                .anyMatch(l -> l.getReceivedQuantity().signum() > 0);
        boolean allReceived = !order.getLines().isEmpty()
                && order.getLines().stream().allMatch(PurchaseOrderLine::isFullyReceived);
        if (allReceived) {
            order.setStatus(PurchaseOrderStatus.RECEIVED);
        } else if (anyReceived) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        } else {
            order.setStatus(PurchaseOrderStatus.SENT);
        }
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

    private String childIdempotencyKey(String idempotencyKey, int lineIndex) {
        String suffix = ":L" + lineIndex;
        if (idempotencyKey.length() + suffix.length() <= IDEMPOTENCY_KEY_MAX_LENGTH) {
            return idempotencyKey + suffix;
        }
        return idempotencyKey.substring(0, IDEMPOTENCY_KEY_MAX_LENGTH - suffix.length()) + suffix;
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
