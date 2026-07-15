package com.erp.manufacturing.module.inventory.dto;

import java.util.List;
import java.util.UUID;

public record InventoryDashboardResponse(
        String scopeType,
        UUID scopeId,
        UUID companyId,
        int totalItemCount,
        int totalWarehouseCount,
        long okCount,
        long lowStockCount,
        long reorderNeededCount,
        InventoryDashboardShortageSummaryResponse shortageSummary,
        List<InventoryAlertLineResponse> topLowStockLines,
        List<StockMovementResponse> recentMovements
) {}
