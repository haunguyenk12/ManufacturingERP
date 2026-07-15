package com.erp.manufacturing.module.reporting.controller.inventory;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.inventory.domain.InventoryAlertStatus;
import com.erp.manufacturing.module.inventory.dto.InventoryAlertLineResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardResponse;
import com.erp.manufacturing.module.inventory.service.InventoryAlertService;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import io.swagger.v3.oas.annotations.Operation;
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

    @GetMapping("/api/v1/reports/inventory-dashboard")
    @Operation(summary = "Get inventory dashboard")
    public ResponseEntity<ApiResponse<InventoryDashboardResponse>> getDashboard(
            @RequestParam ScopeResourceType scopeType,
            @RequestParam UUID scopeId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryAlertService.getDashboard(scopeType, scopeId)));
    }

    @GetMapping("/api/v1/reports/low-stock")
    @Operation(summary = "List low-stock and reorder alerts")
    public ResponseEntity<ApiResponse<List<InventoryAlertLineResponse>>> listAlerts(
            @RequestParam ScopeResourceType scopeType,
            @RequestParam UUID scopeId,
            @RequestParam(required = false) InventoryAlertStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryAlertService.listAlerts(scopeType, scopeId, status)));
    }
}
