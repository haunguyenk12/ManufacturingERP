package com.erp.manufacturing.module.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryDashboardResponse(
        @Schema(description = "COMPANY | PLANT | WAREHOUSE — echoed back from the request")
        String scopeType,
        UUID scopeId,
        @Schema(description = "Company owning the scope, resolved server-side")
        UUID companyId,
        @Schema(description = "Number of distinct items that have an ACTIVE item-warehouse setting "
                + "inside the scope — not the item master count")
        int totalItemCount,
        @Schema(description = "Warehouses inside the scope. Includes INACTIVE warehouses, because "
                + "stock in a deactivated warehouse is still physically there.")
        int totalWarehouseCount,
        @Schema(description = "Item-warehouse lines at OK. The three counts are mutually exclusive and "
                + "sum to the number of ACTIVE settings in scope.")
        long okCount,
        long lowStockCount,
        long reorderNeededCount,
        InventoryDashboardShortageSummaryResponse shortageSummary,
        @Schema(description = "Worst alert lines first (REORDER_NEEDED, then largest shortage), capped "
                + "by lowStockLimit. OK lines are never included.")
        List<InventoryAlertLineResponse> topLowStockLines,
        @Schema(description = "Newest ledger rows in the scope, capped by movementLimit")
        List<DashboardRecentMovementResponse> recentMovements,
        @Schema(description = "When the server computed this snapshot (ISO-8601 UTC). Nothing here is "
                + "cached — every figure is read live.")
        Instant generatedAt
) {}
