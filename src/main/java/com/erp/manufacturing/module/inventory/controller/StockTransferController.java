package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.inventory.dto.StockTransferRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferResponse;
import com.erp.manufacturing.module.inventory.service.StockTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Warehouse-to-warehouse transfer inside one plant (G4-2). {@code X-Plant-Id} is cross-checked on
 * create and list — both name a plant (the warehouses' plant, or {@code plantId}). Detail is
 * identified by the transfer itself and does not take the header (error-handling.md §5.6.1).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Stock Transfers", description = "Warehouse-to-warehouse transfer inside one plant (G4-2)")
public class StockTransferController {

    private final StockTransferService transferService;

    @PostMapping("/v1/inventory/transfers")
    @Operation(summary = "Post a stock transfer (all lines or nothing)")
    public ResponseEntity<ApiResponse<StockTransferResponse>> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @Valid @RequestBody StockTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(transferService.create(request, idempotencyKey, plantHeader)));
    }

    @GetMapping("/v1/inventory/transfers")
    @Operation(summary = "List stock transfers of a plant")
    public ResponseEntity<ApiResponse<PageResult<StockTransferResponse>>> list(
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String search,
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "postedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.list(
                plantId, warehouseId, search, plantHeader,
                PageableFactory.of(page, size, sortBy, sortDir, "transferId"))));
    }

    @GetMapping("/v1/inventory/transfers/{transferId}")
    @Operation(summary = "Get a stock transfer with its lines and movement pairs")
    public ResponseEntity<ApiResponse<StockTransferResponse>> get(@PathVariable UUID transferId) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.get(transferId)));
    }
}
