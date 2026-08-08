package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.dto.ItemCreateRequest;
import com.erp.manufacturing.module.inventory.dto.ItemResponse;
import com.erp.manufacturing.module.inventory.dto.ItemUpdateRequest;
import com.erp.manufacturing.module.inventory.service.ItemService;
import com.erp.manufacturing.common.web.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Items", description = "Inventory item master management")
public class ItemController {

    private final ItemService itemService;

    @PostMapping("/api/v1/companies/{companyId}/items")
    @Operation(summary = "Create item under company")
    public ResponseEntity<ApiResponse<ItemResponse>> createItem(
            @PathVariable UUID companyId,
            @Valid @RequestBody ItemCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(itemService.createItem(companyId, request)));
    }

    @GetMapping("/api/v1/companies/{companyId}/items")
    @Operation(summary = "List items by company")
    public ResponseEntity<ApiResponse<PageResult<ItemResponse>>> listItems(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.listItems(
                companyId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/items/{itemId}")
    @Operation(summary = "Get item")
    public ResponseEntity<ApiResponse<ItemResponse>> getItem(@PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.getItem(itemId)));
    }

    @PatchMapping("/api/v1/items/{itemId}")
    @Operation(summary = "Update item")
    public ResponseEntity<ApiResponse<ItemResponse>> updateItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.updateItem(itemId, request)));
    }

    @DeleteMapping("/api/v1/items/{itemId}")
    @Operation(summary = "Deactivate item")
    public ResponseEntity<ApiResponse<Void>> deactivateItem(@PathVariable UUID itemId) {
        itemService.deactivateItem(itemId);
        return ResponseEntity.ok(ApiResponse.noContent("Item deactivated successfully"));
    }

    @PostMapping("/api/v1/items/{itemId}/activate")
    @Operation(summary = "Activate an item (idempotent — no-op if already ACTIVE; fails if its company is inactive)")
    public ResponseEntity<ApiResponse<ItemResponse>> activateItem(@PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.activateItem(itemId)));
    }

}
