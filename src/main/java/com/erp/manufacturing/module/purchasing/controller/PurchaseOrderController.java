package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.service.PurchaseOrderService;
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
@Tag(name = "Purchase Orders", description = "Purchase order management")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @PostMapping("/api/v1/purchase-orders")
    @Operation(summary = "Create purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> create(
            @Valid @RequestBody PurchaseOrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(purchaseOrderService.create(request)));
    }

    @GetMapping("/api/v1/purchase-orders")
    @Operation(summary = "List purchase orders")
    public ResponseEntity<ApiResponse<PageResult<PurchaseOrderResponse>>> list(
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.list(
                companyId, plantId, warehouseId, supplierId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/purchase-orders/{purchaseOrderId}")
    @Operation(summary = "Get purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> get(@PathVariable UUID purchaseOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.get(purchaseOrderId)));
    }

    @PostMapping("/api/v1/purchase-orders/{purchaseOrderId}/send")
    @Operation(summary = "Send draft purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> send(@PathVariable UUID purchaseOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.send(purchaseOrderId)));
    }

    @PostMapping("/api/v1/purchase-orders/{purchaseOrderId}/cancel")
    @Operation(summary = "Cancel draft purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancel(@PathVariable UUID purchaseOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.cancel(purchaseOrderId)));
    }

}
