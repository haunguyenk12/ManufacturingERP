package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.service.*;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionReceiptService;
import com.erp.manufacturing.module.workorder.service.execution.WipTransactionService;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderVarianceService;
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
@Tag(name = "Manufacturing Execution", description = "Reservations, issue documents, WIP, receipts, and variance")
public class ManufacturingExecutionController {

    private final MaterialReservationService reservationService;
    private final MaterialIssueService materialIssueService;
    private final WipTransactionService wipTransactionService;
    private final ProductionReceiptService productionReceiptService;
    private final WorkOrderVarianceService varianceService;

    @PostMapping("/api/v1/work-orders/{workOrderId}/material-reservations")
    @Operation(summary = "Reserve material for work order")
    public ResponseEntity<ApiResponse<MaterialReservationResponse>> reserveMaterial(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody MaterialReservationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reservationService.reserve(workOrderId, request)));
    }

    @GetMapping("/api/v1/work-orders/{workOrderId}/material-reservations")
    @Operation(summary = "List material reservations")
    public ResponseEntity<ApiResponse<PageResult<MaterialReservationResponse>>> listReservations(
            @PathVariable UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.list(
                workOrderId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @DeleteMapping("/api/v1/work-orders/{workOrderId}/material-reservations/{reservationId}")
    @Operation(summary = "Release material reservation")
    public ResponseEntity<ApiResponse<MaterialReservationResponse>> releaseReservation(
            @PathVariable UUID workOrderId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.release(workOrderId, reservationId)));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/material-issues")
    @Operation(summary = "Post material issue document")
    public ResponseEntity<ApiResponse<MaterialIssueResponse>> postMaterialIssue(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MaterialIssuePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(materialIssueService.post(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/api/v1/work-orders/{workOrderId}/material-issues")
    @Operation(summary = "List material issue documents")
    public ResponseEntity<ApiResponse<PageResult<MaterialIssueResponse>>> listMaterialIssues(
            @PathVariable UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "postedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(materialIssueService.list(
                workOrderId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/wip-transactions")
    @Operation(summary = "Record WIP scrap or rework transaction")
    public ResponseEntity<ApiResponse<WipTransactionResponse>> recordWipTransaction(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody WipTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(wipTransactionService.record(workOrderId, request)));
    }

    @GetMapping("/api/v1/work-orders/{workOrderId}/wip-transactions")
    @Operation(summary = "List WIP transactions")
    public ResponseEntity<ApiResponse<PageResult<WipTransactionResponse>>> listWipTransactions(
            @PathVariable UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(wipTransactionService.list(
                workOrderId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/production-receipts")
    @Operation(summary = "Post production receipt document")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> postProductionReceipt(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionReceiptPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(productionReceiptService.post(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/api/v1/work-orders/{workOrderId}/production-receipts")
    @Operation(summary = "List production receipt documents")
    public ResponseEntity<ApiResponse<PageResult<ProductionReceiptResponse>>> listProductionReceipts(
            @PathVariable UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "postedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.list(
                workOrderId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/work-orders/{workOrderId}/variance")
    @Operation(summary = "Get work order variance")
    public ResponseEntity<ApiResponse<WorkOrderVarianceResponse>> getVariance(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(varianceService.getVariance(workOrderId)));
    }

}
