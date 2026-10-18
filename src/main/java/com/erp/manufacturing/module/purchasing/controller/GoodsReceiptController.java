package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.purchasing.domain.GoodsReceiptStatus;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.service.GoodsReceiptDraftService;
import com.erp.manufacturing.module.purchasing.service.GoodsReceiptService;
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
@Tag(name = "Goods Receipts", description = "Goods receipt posting and retrieval")
public class GoodsReceiptController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final GoodsReceiptService goodsReceiptService;
    private final GoodsReceiptDraftService goodsReceiptDraftService;
    private final PlantContextResolver plantContextResolver;

    @PostMapping("/v1/purchase-orders/{purchaseOrderId}/goods-receipts")
    @Operation(summary = "Create and post a goods receipt in one step (legacy) — received on hold since G7b")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> postGoodsReceipt(
            @PathVariable UUID purchaseOrderId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody GoodsReceiptPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(goodsReceiptService.post(purchaseOrderId, request, idempotencyKey)));
    }

    @GetMapping("/v1/purchase-orders/{purchaseOrderId}/goods-receipts")
    @Operation(summary = "List goods receipts for purchase order")
    public ResponseEntity<ApiResponse<PageResult<GoodsReceiptResponse>>> listGoodsReceipts(
            @PathVariable UUID purchaseOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "postedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(goodsReceiptService.list(
                purchaseOrderId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/goods-receipts")
    @Operation(summary = "List goods receipts of a plant")
    public ResponseEntity<ApiResponse<PageResult<GoodsReceiptResponse>>> listByPlant(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID purchaseOrderId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) GoodsReceiptStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(goodsReceiptDraftService.list(companyId, plantId, purchaseOrderId,
                warehouseId, status, search, receivedDateFrom, receivedDateTo,
                PageableFactory.of(page, size, sortBy, sortDir, "goodsReceiptId"))));
    }

    @GetMapping("/v1/goods-receipts/purchase-order-candidates")
    @Operation(summary = "Purchase orders open for receiving, with their open lines")
    public ResponseEntity<ApiResponse<PageResult<GoodsReceiptCandidateResponse>>> candidates(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(goodsReceiptDraftService.candidates(companyId, plantId, search,
                PageableFactory.of(page, size, "orderDate", "asc", "purchaseOrderId"))));
    }

    @PostMapping("/v1/goods-receipts")
    @Operation(summary = "Create a DRAFT goods receipt (no stock moves)")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> createDraft(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody GoodsReceiptDraftCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(goodsReceiptDraftService.createDraft(request, idempotencyKey)));
    }

    @PatchMapping("/v1/goods-receipts/{goodsReceiptId}")
    @Operation(summary = "Save a DRAFT goods receipt (full line replacement, stable line ids)")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> updateDraft(
            @PathVariable UUID goodsReceiptId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody GoodsReceiptUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(goodsReceiptDraftService.updateDraft(goodsReceiptId, request, idempotencyKey)));
    }

    @PostMapping("/v1/goods-receipts/{goodsReceiptId}/post")
    @Operation(summary = "Post a DRAFT goods receipt: every line on hold, purchase order counters updated")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> post(
            @PathVariable UUID goodsReceiptId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody GoodsReceiptVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(goodsReceiptDraftService.post(goodsReceiptId, request, idempotencyKey)));
    }

    @PostMapping("/v1/goods-receipts/{goodsReceiptId}/cancel")
    @Operation(summary = "Cancel a goods receipt — DRAFT: no stock; POSTED: reversal (needs purchase order manage)")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> cancelGoodsReceipt(
            @PathVariable UUID goodsReceiptId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) GoodsReceiptCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(goodsReceiptService.cancel(goodsReceiptId, request, idempotencyKey)));
    }

    @GetMapping("/v1/goods-receipts/{goodsReceiptId}")
    @Operation(summary = "Get goods receipt")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> getGoodsReceipt(
            @PathVariable UUID goodsReceiptId) {
        return ResponseEntity.ok(ApiResponse.ok(goodsReceiptService.get(goodsReceiptId)));
    }
}
