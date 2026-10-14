package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingRequest;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingResponse;
import com.erp.manufacturing.module.inventory.service.ItemWarehouseSettingService;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
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
    private final PlantContextResolver plantContextResolver;

    @PutMapping("/v1/inventory/item-warehouse-settings")
    @Operation(summary = "Create or update item warehouse setting")
    public ResponseEntity<ApiResponse<ItemWarehouseSettingResponse>> upsert(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @Valid @RequestBody ItemWarehouseSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(settingService.upsert(request, plantHeader)));
    }

    @GetMapping("/v1/inventory/item-warehouse-settings")
    @Operation(summary = "List item warehouse settings")
    public ResponseEntity<ApiResponse<PageResult<ItemWarehouseSettingResponse>>> list(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam(required = false) UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        // Two sources of plant only when plantId is named (error-handling.md 5.6.1); without it the
        // header has nothing to be checked against here.
        if (plantId != null) {
            plantContextResolver.ensureMatches(plantHeader, plantId);
        }
        return ResponseEntity.ok(ApiResponse.ok(settingService.list(
                plantId, warehouseId, itemId, PageableFactory.of(page, size, sortBy, sortDir, "settingId"))));
    }

    @DeleteMapping("/v1/inventory/item-warehouse-settings/{settingId}")
    @Operation(summary = "Deactivate item warehouse setting")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @PathVariable UUID settingId) {
        settingService.deactivate(settingId, plantHeader);
        return ResponseEntity.ok(ApiResponse.noContent("Item warehouse setting deactivated successfully"));
    }

}
