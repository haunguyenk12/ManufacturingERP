package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;

public record InventoryDashboardShortageSummaryResponse(
        int alertLineCount,
        int lowStockLineCount,
        int reorderNeededLineCount,
        BigDecimal safetyStockGapQuantity,
        BigDecimal reorderPointGapQuantity
) {}
