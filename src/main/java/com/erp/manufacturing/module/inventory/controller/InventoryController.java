package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.dto.*;
import com.erp.manufacturing.module.inventory.service.InventoryService;
import com.erp.manufacturing.common.web.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock movements and balances")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/api/v1/inventory/receive")
    @Operation(summary = "Receive stock")
    public ResponseEntity<ApiResponse<StockMovementResponse>> receive(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody StockReceiveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.receive(request, idempotencyKey)));
    }

    @PostMapping("/api/v1/inventory/issue")
    @Operation(summary = "Issue stock")
    public ResponseEntity<ApiResponse<StockMovementResponse>> issue(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody StockIssueRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.issue(request, idempotencyKey)));
    }

    @PostMapping("/api/v1/inventory/adjust")
    @Operation(summary = "Adjust stock")
    public ResponseEntity<ApiResponse<StockMovementResponse>> adjust(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.adjust(request, idempotencyKey)));
    }

    @GetMapping("/api/v1/inventory/balances")
    @Operation(summary = "List stock balances")
    public ResponseEntity<ApiResponse<PageResult<StockBalanceResponse>>> listBalances(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.listBalances(
                warehouseId, itemId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/inventory/movements")
    @Operation(summary = "List stock movements")
    public ResponseEntity<ApiResponse<PageResult<StockMovementResponse>>> listMovements(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.listMovements(
                warehouseId, itemId, lotId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

}
