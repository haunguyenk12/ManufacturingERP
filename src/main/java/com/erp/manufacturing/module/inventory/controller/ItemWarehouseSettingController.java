package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingRequest;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingResponse;
import com.erp.manufacturing.module.inventory.service.ItemWarehouseSettingService;
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
@Tag(name = "Inventory Settings", description = "Item and warehouse stock thresholds")
public class ItemWarehouseSettingController {

    private final ItemWarehouseSettingService settingService;

    @PutMapping("/api/v1/inventory/item-warehouse-settings")
    @Operation(summary = "Create or update item warehouse setting")
    public ResponseEntity<ApiResponse<ItemWarehouseSettingResponse>> upsert(
            @Valid @RequestBody ItemWarehouseSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(settingService.upsert(request)));
    }

    @GetMapping("/api/v1/inventory/item-warehouse-settings")
    @Operation(summary = "List item warehouse settings")
    public ResponseEntity<ApiResponse<PageResult<ItemWarehouseSettingResponse>>> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(settingService.list(
                warehouseId, itemId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @DeleteMapping("/api/v1/inventory/item-warehouse-settings/{settingId}")
    @Operation(summary = "Deactivate item warehouse setting")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID settingId) {
        settingService.deactivate(settingId);
        return ResponseEntity.ok(ApiResponse.noContent("Item warehouse setting deactivated successfully"));
    }

}
