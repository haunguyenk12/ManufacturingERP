package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.dto.InventoryLotDetailResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryLotResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryLotStatusChangeRequest;
import com.erp.manufacturing.module.inventory.service.InventoryLotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Inventory Lots", description = "Lot list/detail and status lifecycle (C2-2)")
public class InventoryLotController {

    private final InventoryLotService lotService;

    @GetMapping("/v1/inventory/lots")
    @Operation(summary = "List inventory lots")
    public ResponseEntity<ApiResponse<PageResult<InventoryLotResponse>>> list(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) LotStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant expiryFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant expiryTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(lotService.list(warehouseId, itemId, status, search,
                expiryFrom, expiryTo, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/inventory/lots/{lotId}")
    @Operation(summary = "Get inventory lot detail")
    public ResponseEntity<ApiResponse<InventoryLotDetailResponse>> get(
            @PathVariable UUID lotId,
            @Parameter(name = "warehouseId", in = ParameterIn.QUERY, required = false,
                    description = "Warehouse scope for the detail view; required for plant/warehouse-scoped users")
            @RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok(lotService.get(lotId, warehouseId)));
    }

    @PostMapping("/v1/inventory/lots/{lotId}/status")
    @Operation(summary = "Change inventory lot status")
    public ResponseEntity<ApiResponse<InventoryLotResponse>> changeStatus(
            @PathVariable UUID lotId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InventoryLotStatusChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(lotService.changeStatus(lotId, request, idempotencyKey)));
    }
}
