package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionResponse;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionResult;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcQueueLineResponse;
import com.erp.manufacturing.module.purchasing.service.PurchaseQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/** G7c: purchase QC — the queue of received lines on hold, decisions, and their history. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Purchase QC", description = "Quality decisions on goods received from purchase orders")
public class PurchaseQcController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PurchaseQualityService purchaseQualityService;
    private final PlantContextResolver plantContextResolver;

    @GetMapping("/v1/purchase-qc")
    @Operation(summary = "Goods receipt lines waiting for purchase QC (no prices)")
    public ResponseEntity<ApiResponse<PageResult<PurchaseQcQueueLineResponse>>> queue(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        // The query orders oldest receipt first, then line id — a client sort would break that tie-break.
        return ResponseEntity.ok(ApiResponse.ok(purchaseQualityService.queue(companyId, plantId, warehouseId, search,
                receivedDateFrom, receivedDateTo, PageableFactory.unsorted(page, size))));
    }

    @PostMapping("/v1/goods-receipts/{goodsReceiptId}/lines/{goodsReceiptLineId}/qc")
    @Operation(summary = "Record a purchase QC decision on one goods receipt line")
    public ResponseEntity<ApiResponse<PurchaseQcDecisionResult>> decide(
            @PathVariable UUID goodsReceiptId,
            @PathVariable UUID goodsReceiptLineId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseQcDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                purchaseQualityService.decide(goodsReceiptId, goodsReceiptLineId, request, idempotencyKey)));
    }

    @GetMapping("/v1/goods-receipts/{goodsReceiptId}/quality-decisions")
    @Operation(summary = "Purchase QC decisions of a goods receipt, newest first")
    public ResponseEntity<ApiResponse<PageResult<PurchaseQcDecisionResponse>>> decisions(
            @PathVariable UUID goodsReceiptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                purchaseQualityService.decisions(goodsReceiptId, PageableFactory.unsorted(page, size))));
    }
}
