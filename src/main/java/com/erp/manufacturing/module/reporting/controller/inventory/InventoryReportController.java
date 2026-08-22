package com.erp.manufacturing.module.reporting.controller.inventory;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.inventory.domain.InventoryAlertStatus;
import com.erp.manufacturing.module.inventory.dto.InventoryAlertLineResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardResponse;
import com.erp.manufacturing.module.inventory.service.InventoryAlertService;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Inventory Reports", description = "Inventory dashboard and stock alerts")
public class InventoryReportController {

    private final InventoryAlertService inventoryAlertService;

    @GetMapping("/v1/reports/inventory-dashboard")
    @Operation(summary = "Get inventory dashboard",
            description = """
                    One aggregate read for the dashboard: threshold counts, a shortage summary, the
                    worst alert lines and the newest ledger rows, all resolved to display labels so no
                    follow-up call to Item, Warehouse or User is needed.

                    Counts are per item-warehouse line, since safety stock and reorder point are
                    configured per warehouse. Requires PERM_INVENTORY_READ on the requested scope;
                    a scope outside the caller's assignments is 403 PERMISSION_DENIED, an unknown
                    scope id is 404 ENTITY_NOT_FOUND.""")
    public ResponseEntity<ApiResponse<InventoryDashboardResponse>> getDashboard(
            @Parameter(description = "COMPANY aggregates every plant/warehouse of the company, PLANT "
                    + "every warehouse of the plant, WAREHOUSE exactly one warehouse")
            @RequestParam ScopeResourceType scopeType,
            @Parameter(description = "Id of the company, plant or warehouse named by scopeType")
            @RequestParam UUID scopeId,
            @Parameter(description = "Alert lines to return (1-20, default 10). Out-of-range values are "
                    + "clamped, not rejected.")
            @RequestParam(required = false) Integer lowStockLimit,
            @Parameter(description = "Ledger rows to return (1-20, default 10). Out-of-range values are "
                    + "clamped, not rejected.")
            @RequestParam(required = false) Integer movementLimit) {
        return ResponseEntity.ok(ApiResponse.ok(
                inventoryAlertService.getDashboard(scopeType, scopeId, lowStockLimit, movementLimit)));
    }

    @GetMapping("/v1/reports/low-stock")
    @Operation(summary = "List low-stock and reorder alerts",
            description = """
                    Every item-warehouse line in the scope that has an ACTIVE setting, optionally
                    filtered by status. Deliberately not paginated: it returns one row per configured
                    threshold. Same alert-line shape and ordering as the dashboard's topLowStockLines,
                    but without the OK lines being dropped.""")
    public ResponseEntity<ApiResponse<List<InventoryAlertLineResponse>>> listAlerts(
            @RequestParam ScopeResourceType scopeType,
            @RequestParam UUID scopeId,
            @Parameter(description = "OK | LOW_STOCK | REORDER_NEEDED. Absent returns all three.")
            @RequestParam(required = false) InventoryAlertStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryAlertService.listAlerts(scopeType, scopeId, status)));
    }
}
