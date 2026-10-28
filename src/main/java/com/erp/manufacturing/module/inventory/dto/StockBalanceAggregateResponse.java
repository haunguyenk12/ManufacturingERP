package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockBalanceAggregateResponse(
        UUID itemId,
        String itemCode,
        String itemName,
        String uom,
        UUID warehouseId,
        BigDecimal onHandQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        BigDecimal qualityHoldQuantity,
        BigDecimal rejectedQuantity,
        BigDecimal expiredQuantity,
        BigDecimal heldLotQuantity,
        Long lotCount,
        Instant updatedAt,
        /**
         * CF-3: of {@code qualityHoldQuantity} (non-lot stock), the part purchase QC has not decided yet, and the part it
         * rejected. Lot stock is not in these — its QC state is on the lot ({@code heldLotQuantity}/{@code rejectedQuantity}).
         */
        BigDecimal pendingPurchaseQcQuantity,
        BigDecimal rejectedPurchaseQcQuantity,
        /**
         * {@code qualityHoldQuantity − pending − rejected}, not clamped: hold no goods receipt explains (production
         * receipt QC, or a data error if negative).
         */
        BigDecimal unclassifiedQualityHoldQuantity,
        /** {@code true} when purchase QC explains the whole quality hold ({@code unclassifiedQualityHoldQuantity == 0}). */
        boolean qualityBreakdownKnown
) {
    /** Pre-CF-3 shape: nothing held by purchase QC, so the whole quality hold is unclassified. */
    public StockBalanceAggregateResponse(UUID itemId, String itemCode, String itemName, String uom, UUID warehouseId,
                                         BigDecimal onHandQuantity, BigDecimal reservedQuantity,
                                         BigDecimal availableQuantity, BigDecimal qualityHoldQuantity,
                                         BigDecimal rejectedQuantity, BigDecimal expiredQuantity,
                                         BigDecimal heldLotQuantity, Long lotCount, Instant updatedAt) {
        this(itemId, itemCode, itemName, uom, warehouseId, onHandQuantity, reservedQuantity, availableQuantity,
                qualityHoldQuantity, rejectedQuantity, expiredQuantity, heldLotQuantity, lotCount, updatedAt,
                BigDecimal.ZERO, BigDecimal.ZERO, qualityHoldQuantity, qualityHoldQuantity.signum() == 0);
    }
}
