package com.erp.manufacturing.module.costing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.costing.dto.ItemStandardCostRequest;
import com.erp.manufacturing.module.costing.dto.ItemStandardCostResponse;
import com.erp.manufacturing.module.costing.service.ItemStandardCostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Costing", description = "Item standard cost master data")
public class ItemStandardCostController {

    private final ItemStandardCostService itemStandardCostService;

    @PutMapping("/v1/companies/{companyId}/items/{itemId}/standard-cost")
    @Operation(summary = "Create or update an item's standard cost")
    public ResponseEntity<ApiResponse<ItemStandardCostResponse>> upsert(
            @PathVariable UUID companyId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemStandardCostRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemStandardCostService.upsert(companyId, itemId, request)));
    }

    @GetMapping("/v1/companies/{companyId}/items/{itemId}/standard-cost")
    @Operation(summary = "Get an item's standard cost")
    public ResponseEntity<ApiResponse<ItemStandardCostResponse>> get(
            @PathVariable UUID companyId,
            @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok(itemStandardCostService.get(companyId, itemId)));
    }

    @GetMapping("/v1/companies/{companyId}/items/standard-costs")
    @Operation(summary = "List item standard costs")
    public ResponseEntity<ApiResponse<PageResult<ItemStandardCostResponse>>> list(
            @PathVariable UUID companyId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(itemStandardCostService.list(
                companyId, itemId, PageableFactory.of(page, size, sortBy, sortDir))));
    }
}
