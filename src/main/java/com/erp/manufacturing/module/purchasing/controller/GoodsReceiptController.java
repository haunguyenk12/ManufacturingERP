package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptCancelRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptPostRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptResponse;
import com.erp.manufacturing.module.purchasing.service.GoodsReceiptService;
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
@Tag(name = "Goods Receipts", description = "Goods receipt posting and retrieval")
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    @PostMapping("/api/v1/purchase-orders/{purchaseOrderId}/goods-receipts")
    @Operation(summary = "Post goods receipt for purchase order")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> postGoodsReceipt(
            @PathVariable UUID purchaseOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody GoodsReceiptPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(goodsReceiptService.post(purchaseOrderId, request, idempotencyKey)));
    }

    @GetMapping("/api/v1/purchase-orders/{purchaseOrderId}/goods-receipts")
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

    @PostMapping("/api/v1/goods-receipts/{goodsReceiptId}/cancel")
    @Operation(summary = "Cancel a posted goods receipt")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> cancelGoodsReceipt(
            @PathVariable UUID goodsReceiptId,
            @RequestBody(required = false) GoodsReceiptCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(goodsReceiptService.cancel(goodsReceiptId, request)));
    }

    @GetMapping("/api/v1/goods-receipts/{goodsReceiptId}")
    @Operation(summary = "Get goods receipt")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> getGoodsReceipt(
            @PathVariable UUID goodsReceiptId) {
        return ResponseEntity.ok(ApiResponse.ok(goodsReceiptService.get(goodsReceiptId)));
    }

}
