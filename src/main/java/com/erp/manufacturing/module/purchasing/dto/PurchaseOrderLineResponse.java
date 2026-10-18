package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderLineResponse(
        UUID purchaseOrderLineId,
        UUID purchaseRequisitionLineId,
        UUID itemId,
        String itemCode,
        String itemName,
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        BigDecimal remainingQuantity,
        BigDecimal unitPrice,
        String currencyCode,
        LocalDate expectedDate,
        // ── G7a, additive ──
        String uomCode,
        BigDecimal closedQuantity,
        /** ordered − received − closed; {@code remainingQuantity} carries the same value. */
        BigDecimal openQuantity,
        List<PurchaseOrderLineSourceResponse> sources
) {

    /** Pre-G7a arity, kept so callers building the legacy shape still compile. */
    public PurchaseOrderLineResponse(UUID purchaseOrderLineId, UUID purchaseRequisitionLineId, UUID itemId,
                                     String itemCode, String itemName, BigDecimal orderedQuantity,
                                     BigDecimal receivedQuantity, BigDecimal remainingQuantity, BigDecimal unitPrice,
                                     String currencyCode, LocalDate expectedDate) {
        this(purchaseOrderLineId, purchaseRequisitionLineId, itemId, itemCode, itemName, orderedQuantity,
                receivedQuantity, remainingQuantity, unitPrice, currencyCode, expectedDate, null, BigDecimal.ZERO,
                remainingQuantity, List.of());
    }
}
