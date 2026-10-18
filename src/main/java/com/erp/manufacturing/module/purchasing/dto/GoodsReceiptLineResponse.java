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
        List<UUID> movementIds
) {

    /** Pre-G7b arity. */
    public GoodsReceiptLineResponse(UUID goodsReceiptLineId, UUID purchaseOrderLineId, UUID itemId, String itemCode,
                                    String itemName, UUID lotId, String lotCode, BigDecimal receivedQuantity,
                                    UUID stockMovementId) {
        this(goodsReceiptLineId, purchaseOrderLineId, itemId, itemCode, itemName, lotId, lotCode, receivedQuantity,
                stockMovementId, null, null, null, null, null, null,
                stockMovementId == null ? List.of() : List.of(stockMovementId));
    }
}
