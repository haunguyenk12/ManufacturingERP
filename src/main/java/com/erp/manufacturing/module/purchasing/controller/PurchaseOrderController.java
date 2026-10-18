package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Purchase Orders", description = "Purchase order management")
public class PurchaseOrderController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PurchaseOrderService purchaseOrderService;
    private final PlantContextResolver plantContextResolver;

    @PostMapping("/v1/purchase-orders")
    @Operation(summary = "Create purchase order with a client-chosen number (legacy)")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> create(
            @Valid @RequestBody PurchaseOrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(purchaseOrderService.create(request)));
    }

    @PostMapping("/v1/purchase-orders/drafts")
    @Operation(summary = "Create a DRAFT purchase order numbered by the server")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createDraft(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseOrderDraftCreateRequest request) {
        plantContextResolver.ensureMatches(plantHeader, request.plantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(purchaseOrderService.createDraft(request, idempotencyKey)));
    }

    @GetMapping("/v1/purchase-orders")
    @Operation(summary = "List purchase orders")
    public ResponseEntity<ApiResponse<PageResult<PurchaseOrderResponse>>> list(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.list(
                companyId, plantId, warehouseId, supplierId, status, search, orderDateFrom, orderDateTo,
                PageableFactory.of(page, size, sortBy, sortDir, "purchaseOrderId"))));
    }

    @GetMapping("/v1/purchase-orders/{purchaseOrderId}")
    @Operation(summary = "Get purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> get(@PathVariable UUID purchaseOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.get(purchaseOrderId)));
    }

    @PatchMapping("/v1/purchase-orders/{purchaseOrderId}")
    @Operation(summary = "Save a DRAFT purchase order (full line replacement, stable line ids)")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> update(
            @PathVariable UUID purchaseOrderId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseOrderUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.updateDraft(purchaseOrderId, request, idempotencyKey)));
    }

    @PostMapping("/v1/purchase-orders/{purchaseOrderId}/confirm")
    @Operation(summary = "Confirm a DRAFT purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> confirm(
            @PathVariable UUID purchaseOrderId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseOrderVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.confirm(purchaseOrderId, request, idempotencyKey)));
    }

    @PostMapping("/v1/purchase-orders/{purchaseOrderId}/send")
    @Operation(summary = "Send (confirm) a DRAFT purchase order — legacy, no body")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> send(@PathVariable UUID purchaseOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.send(purchaseOrderId)));
    }

    @PostMapping("/v1/purchase-orders/{purchaseOrderId}/cancel")
    @Operation(summary = "Cancel a purchase order — without a body DRAFT only; with {expectedVersion, reason} also CONFIRMED")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancel(
            @PathVariable UUID purchaseOrderId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) PurchaseOrderReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.cancel(purchaseOrderId, request, idempotencyKey)));
    }

    @PostMapping("/v1/purchase-orders/{purchaseOrderId}/close")
    @Operation(summary = "Close the open remainder of a confirmed purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> close(
            @PathVariable UUID purchaseOrderId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseOrderReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.close(purchaseOrderId, request, idempotencyKey)));
    }
}
