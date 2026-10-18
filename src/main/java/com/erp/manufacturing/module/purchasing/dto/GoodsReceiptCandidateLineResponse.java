package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** G7b: an open purchase order line a goods receipt can take ({@code openQuantity > 0}). */
public record GoodsReceiptCandidateLineResponse(
        UUID purchaseOrderLineId,
        UUID itemId,
        String itemCode,
        String itemName,
        String uomCode,
        /** {@code LOT_TRACKED} / {@code NON_TRACKED} / {@code SERIAL_TRACKED} (serial cannot be received yet). */
        String trackingPolicy,
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        BigDecimal closedQuantity,
        BigDecimal openQuantity,
        LocalDate expectedDate,
        BigDecimal unitPrice,
        String currencyCode
) {}
