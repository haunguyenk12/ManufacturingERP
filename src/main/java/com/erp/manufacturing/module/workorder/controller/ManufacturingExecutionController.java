package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.service.*;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionExecutionService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionReceiptService;
import com.erp.manufacturing.module.workorder.service.execution.WipTransactionService;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderVarianceService;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Manufacturing Execution", description = "Reservations, issue documents, WIP, receipts, and variance")
public class ManufacturingExecutionController {

    private final MaterialReservationService reservationService;
    private final MaterialIssueService materialIssueService;
    private final WipTransactionService wipTransactionService;
    private final ProductionExecutionService productionExecutionService;
    private final ProductionReceiptService productionReceiptService;
    private final WorkOrderVarianceService varianceService;
    /**
     * Used by the endpoints on this controller that name a plant explicitly — the two candidate
     * screens and the two flat lists — because those are the ones with a second source of plant to
     * cross-check against ({@code error-handling.md §5.6.1}). Endpoints identified by their
     * aggregate ({@code /work-orders/{id}/...}, and the flat
     * {@code GET /production-executions?workOrderId=}) deliberately ignore the header: the id
     * <em>is</em> the scope, so there is nothing to disagree with. The boundary is per endpoint, not
     * per controller.
     */
    private final PlantContextResolver plantContextResolver;

    @PostMapping("/api/v1/work-orders/{workOrderId}/material-reservations")
    @Operation(summary = "Reserve material for work order")
    public ResponseEntity<ApiResponse<MaterialReservationResponse>> reserveMaterial(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody MaterialReservationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reservationService.reserve(workOrderId, request)));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/reserve")
    @Operation(summary = "Reserve all short components automatically (FEFO)",
            description = "Allocates available stock across the plant's warehouses, earliest expiry "
                    + "first. Components with no stock left are simply skipped — check "
                    + "GET /material-readiness afterwards. Use POST /material-reservations to pick a "
                    + "specific warehouse or lot instead.")
    public ResponseEntity<ApiResponse<List<MaterialReservationResponse>>> reserveAutomatically(
            @PathVariable UUID workOrderId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reservationService.reserveAutomatically(workOrderId)));
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

    /**
     * Flat material issue history (spec §4.1). Plant-scoped, so it cross-checks {@code X-Plant-Id};
     * {@code workOrderId} narrows it and is optional.
     */
    @GetMapping("/api/v1/material-issues")
    @Operation(summary = "List material issue documents across a plant",
            description = "Spec §4.1. Flat form of "
                    + "GET /work-orders/{workOrderId}/material-issues; pass workOrderId to narrow it "
                    + "to one work order.")
    public ResponseEntity<ApiResponse<PageResult<MaterialIssueResponse>>> listMaterialIssuesByPlant(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "postedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(materialIssueService.listByPlant(
                plantId, workOrderId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    /**
     * Flat material issue endpoint (spec §4.1). The multi-line
     * {@code POST /work-orders/{id}/material-issues} above is kept for the cases the shop-floor UI
     * does not cover — over-issue with a justification, several components in one document.
     */
    @PostMapping("/api/v1/material-issues")
    @Operation(summary = "Issue one reserved component",
            description = "Single-line form of POST /work-orders/{workOrderId}/material-issues. "
                    + "Consumes the given reservation; does not support over-issue override.")
    public ResponseEntity<ApiResponse<MaterialIssueResponse>> postFlatMaterialIssue(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MaterialIssueFlatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(materialIssueService.postFlat(request, idempotencyKey)));
    }

    @GetMapping("/api/v1/production-executions/candidates")
    @Operation(summary = "List work orders the shop floor may report production against",
            description = "Spec §5.1. Returns RELEASED or IN_PROGRESS work orders whose cumulative "
                    + "good quantity has not yet reached the plan — a work order at its plan can no "
                    + "longer accept a report, so it is not a candidate (invariant B75).")
    public ResponseEntity<ApiResponse<PageResult<ProductionExecutionCandidateResponse>>> listExecutionCandidates(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "plannedEndAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(productionExecutionService.listCandidates(
                plantId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    /**
     * Flat form of {@code GET /work-orders/{workOrderId}/production-executions} (spec §5.1).
     *
     * <p>🔴 Deliberately does <b>not</b> accept {@code X-Plant-Id}, unlike the two endpoints around
     * it. It is identified by its aggregate, so {@code workOrderId} <em>is</em> the scope and there
     * is no second value for the header to disagree with ({@code error-handling.md §5.6.1}, row 2);
     * {@code @PreAuthorize} on the service already refuses a work order in another plant. Wiring the
     * header in "for consistency with the rest of the controller" would add a check with nothing to
     * check — the boundary is per endpoint, not per controller.
     */
    @GetMapping("/api/v1/production-executions")
    @Operation(summary = "List shop floor production reports for a work order",
            description = "Spec §5.1. Flat form of "
                    + "GET /work-orders/{workOrderId}/production-executions.")
    public ResponseEntity<ApiResponse<PageResult<ProductionExecutionResponse>>> listProductionExecutionsFlat(
            @RequestParam UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(productionExecutionService.list(
                workOrderId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/production-executions")
    @Operation(summary = "Report shop floor production",
            description = "Records good/scrap/rework quantities. This — not the production receipt — "
                    + "is what advances and completes the work order. Fails with 409 "
                    + "PLANNED_QUANTITY_EXCEEDED when cumulative good would exceed the plan.")
    public ResponseEntity<ApiResponse<ProductionExecutionResponse>> reportProduction(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionExecutionPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(productionExecutionService.report(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/api/v1/work-orders/{workOrderId}/production-executions")
    @Operation(summary = "List shop floor production reports")
    public ResponseEntity<ApiResponse<PageResult<ProductionExecutionResponse>>> listProductionExecutions(
            @PathVariable UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(productionExecutionService.list(
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

    @GetMapping("/api/v1/production-receipts/candidates")
    @Operation(summary = "List work orders with finished output still to be warehoused",
            description = "Spec §6.2. Returns RELEASED, IN_PROGRESS or COMPLETED work orders whose "
                    + "produced good quantity has not yet been fully receipted, already net of "
                    + "receipts still in DRAFT or PENDING_APPROVAL (invariant B76). COMPLETED is "
                    + "included on purpose: the shop floor being done does not mean the goods "
                    + "reached the racks.")
    public ResponseEntity<ApiResponse<PageResult<ProductionReceiptCandidateResponse>>> listReceiptCandidates(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "plannedEndAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.listCandidates(
                plantId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    /** Flat plant-scoped receipt list (spec §6.2); {@code status} is an optional filter. */
    @GetMapping("/api/v1/production-receipts")
    @Operation(summary = "List production receipt documents across a plant",
            description = "Spec §6.2. Flat form of "
                    + "GET /work-orders/{workOrderId}/production-receipts.")
    public ResponseEntity<ApiResponse<PageResult<ProductionReceiptResponse>>> listProductionReceiptsByPlant(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(required = false) ProductionReceiptStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "postedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.listByPlant(
                plantId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/production-receipts")
    @Operation(summary = "Create production receipt document",
            description = "Creates a DRAFT receipt. No stock movement is created and the work order "
                    + "completed quantity is unchanged until the receipt is approved.")
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

    @PostMapping("/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/submit")
    @Operation(summary = "Submit draft production receipt for approval",
            description = "Moves a DRAFT receipt to PENDING_APPROVAL. Still no inventory impact.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> submitProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId) {
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.submit(workOrderId, receiptId)));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/approve")
    @Operation(summary = "Approve pending production receipt",
            description = "Creates the RECEIVE stock movements and advances the work order. "
                    + "Newly created output lots start in HOLD status.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> approveProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId) {
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.approve(workOrderId, receiptId)));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/reject")
    @Operation(summary = "Reject pending production receipt",
            description = "Closes the receipt without any inventory impact.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> rejectProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @Valid @RequestBody ProductionReceiptRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.reject(workOrderId, receiptId, request)));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/qc-disposition")
    @Operation(summary = "Record QC disposition for an approved production receipt",
            description = "Releases the output lots from HOLD to AVAILABLE, or fails them to REJECTED. "
                    + "On-hand quantity is unchanged either way — only usability changes.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> qcDispositionProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @Valid @RequestBody ProductionReceiptQcDispositionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productionReceiptService.qcDisposition(workOrderId, receiptId, request)));
    }

    @GetMapping("/api/v1/work-orders/{workOrderId}/variance")
    @Operation(summary = "Get work order variance")
    public ResponseEntity<ApiResponse<WorkOrderVarianceResponse>> getVariance(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(varianceService.getVariance(workOrderId)));
    }

}
