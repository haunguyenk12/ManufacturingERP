package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptLineResponse(
        UUID goodsReceiptLineId,
        UUID purchaseOrderLineId,
        UUID itemId,
        String itemCode,
        String itemName,
        UUID lotId,
        String lotCode,
        BigDecimal receivedQuantity,
        /** {@code null} while the receipt is DRAFT. */
        UUID stockMovementId,
        // ── G7b, additive ──
        /** {@code LOT_TRACKED} / {@code NON_TRACKED} / {@code SERIAL_TRACKED}. */
        String trackingPolicy,
        String uomCode,
        /** Price of the purchase order line — server-owned, never sent by the client. */
        BigDecimal unitPrice,
        String currencyCode,
        LocalDate expiresOn,
        /** Status of the lot the line landed in ({@code HOLD} right after posting); {@code null} without a lot. */
        String lotStatus,
        /** Receive movements of the line ({@code []} while DRAFT). */
        List<UUID> movementIds,
        // ── G7c, additive ──
        /** Sum of purchase QC decisions; {@code 0} before any. */
        BigDecimal acceptedQuantity,
        BigDecimal rejectedQuantity,
        /** {@code received − accepted − rejected} on a POSTED receipt that went on hold; {@code null} otherwise. */
        BigDecimal pendingQuantity,
        /** Owner of the quality hold of a non-tracked line (= the line id, D7); {@code null} for a tracked line or before posting. */
        UUID qualityBucketId
) {

    /** G7b arity: no purchase QC figures. */
    public GoodsReceiptLineResponse(UUID goodsReceiptLineId, UUID purchaseOrderLineId, UUID itemId, String itemCode,
                                    String itemName, UUID lotId, String lotCode, BigDecimal receivedQuantity,
                                    UUID stockMovementId, String trackingPolicy, String uomCode, BigDecimal unitPrice,
                                    String currencyCode, LocalDate expiresOn, String lotStatus, List<UUID> movementIds) {
        this(goodsReceiptLineId, purchaseOrderLineId, itemId, itemCode, itemName, lotId, lotCode, receivedQuantity,
                stockMovementId, trackingPolicy, uomCode, unitPrice, currencyCode, expiresOn, lotStatus, movementIds,
                null, null, null, null);
    }

    /** Pre-G7b arity. */
    public GoodsReceiptLineResponse(UUID goodsReceiptLineId, UUID purchaseOrderLineId, UUID itemId, String itemCode,
                                    String itemName, UUID lotId, String lotCode, BigDecimal receivedQuantity,
                                    UUID stockMovementId) {
        this(goodsReceiptLineId, purchaseOrderLineId, itemId, itemCode, itemName, lotId, lotCode, receivedQuantity,
                stockMovementId, null, null, null, null, null, null,
                stockMovementId == null ? List.of() : List.of(stockMovementId));
    }
}
