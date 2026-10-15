package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;

/**
 * Result of a stock count (G4-3). {@code movement} is the ADJUST_IN/ADJUST_OUT row the count wrote;
 * {@code balance} is the position after it, including its new {@code version}.
 */
public record StockCountResponse(
        BigDecimal previousQuantity,
        BigDecimal countedQuantity,
        BigDecimal quantityDelta,
        StockMovementResponse movement,
        StockBalanceResponse balance
) {}
