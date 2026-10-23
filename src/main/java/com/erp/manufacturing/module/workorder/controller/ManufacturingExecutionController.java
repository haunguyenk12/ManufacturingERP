package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.operation.OperationInputRequest;
import com.erp.manufacturing.module.workorder.dto.operation.ProductionCompletionRequest;
import com.erp.manufacturing.module.workorder.dto.operation.ProductionCompletionResponse;
import com.erp.manufacturing.module.workorder.dto.operation.WorkOrderOperationProgressResponse;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.service.*;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueCandidateService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.OperationService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Manufacturing Execution", description = "Reservations, issue documents, WIP, receipts, and variance")
public class ManufacturingExecutionController {

    private final MaterialReservationService reservationService;
    private final MaterialIssueCandidateService materialIssueCandidateService;
    private final MaterialIssueService materialIssueService;
    private final WipTransactionService wipTransactionService;
    private final ProductionExecutionService productionExecutionService;
    private final OperationService operationService;
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

    @PostMapping("/v1/work-orders/{workOrderId}/material-reservations")
    @Operation(summary = "Reserve material for work order")
    public ResponseEntity<ApiResponse<MaterialReservationResponse>> reserveMaterial(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MaterialReservationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reservationService.reserve(workOrderId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/reserve")
    @Operation(summary = "Reserve all short components automatically (FEFO)",
            description = "Allocates available stock earliest expiry first — from the component line's own "
                    + "warehouse when it has one (G8a), otherwise across the plant's warehouses. Components "
                    + "with no stock left are simply skipped — check GET /material-readiness afterwards. Body "
                    + "{expectedWorkOrderVersion} is required (G8).")
    public ResponseEntity<ApiResponse<List<MaterialReservationResponse>>> reserveAutomatically(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) WorkOrderVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reservationService.reserveAutomatically(workOrderId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/release-reservations")
    @Operation(summary = "Release every unissued reservation of the work order",
            description = "G8a: gives the unissued part of each ACTIVE reservation back to free stock. No stock "
                    + "movement — on hand never changed. Body {expectedWorkOrderVersion} is required.")
    public ResponseEntity<ApiResponse<WorkOrderReservationReleaseResponse>> releaseReservations(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) WorkOrderVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.releaseAll(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/material-reservations")
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

    @DeleteMapping("/v1/work-orders/{workOrderId}/material-reservations/{reservationId}")
    @Operation(summary = "Release material reservation",
            description = "Query parameter expectedWorkOrderVersion is required (G8).")
    public ResponseEntity<ApiResponse<MaterialReservationResponse>> releaseReservation(
            @PathVariable UUID workOrderId,
            @PathVariable UUID reservationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(required = false) Long expectedWorkOrderVersion) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.release(
                workOrderId, reservationId, expectedWorkOrderVersion, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/material-issues")
    @Operation(summary = "Post material issue document")
    public ResponseEntity<ApiResponse<MaterialIssueResponse>> postMaterialIssue(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MaterialIssuePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(materialIssueService.post(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/material-issues")
    @Operation(summary = "List material issue documents")
    public ResponseEntity<ApiResponse<PageResult<MaterialIssueResponse>>> listMaterialIssues(
            @PathVariable UUID workOrderId,
            @RequestParam(required = false) MaterialIssueStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "requestedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(materialIssueService.list(
                workOrderId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/material-issues/{issueId}/approve")
    @Operation(summary = "Approve an Over-BOM material request and atomically post stock",
            description = "G8b: body {expectedWorkOrderVersion, expectedIssueVersion, reason?} — both versions "
                    + "required. Approving your own request also needs PERM_MATERIAL_ISSUE_OVERRIDE.")
    public ResponseEntity<ApiResponse<MaterialIssueResponse>> approveMaterialIssue(
            @PathVariable UUID workOrderId,
            @PathVariable UUID issueId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MaterialIssueVersionedDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(materialIssueService.approve(workOrderId, issueId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/material-issues/{issueId}/reject")
    @Operation(summary = "Reject an Over-BOM material request without stock impact",
            description = "G8b: body {expectedWorkOrderVersion, expectedIssueVersion, reason} — reason 5–2000 characters.")
    public ResponseEntity<ApiResponse<MaterialIssueResponse>> rejectMaterialIssue(
            @PathVariable UUID workOrderId,
            @PathVariable UUID issueId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MaterialIssueVersionedDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(materialIssueService.reject(workOrderId, issueId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/material-issues/{issueId}/cancel")
    @Operation(summary = "Withdraw your own pending material request",
            description = "G8b: only the requester; PENDING_APPROVAL → CANCELLED, no stock effect. Body "
                    + "{expectedWorkOrderVersion, expectedIssueVersion, reason} — reason 5–2000 characters.")
    public ResponseEntity<ApiResponse<MaterialIssueResponse>> cancelMaterialIssue(
            @PathVariable UUID workOrderId,
            @PathVariable UUID issueId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MaterialIssueVersionedDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                materialIssueService.cancelByRequester(workOrderId, issueId, request, idempotencyKey)));
    }

    @GetMapping("/v1/material-issues/{issueId}")
    @Operation(summary = "One material issue with its decision context",
            description = "G8b: the issue plus, per component, requirement / gross issued / requested / extra over "
                    + "the BOM / current availability of its sources. Readable with MANAGE, APPROVE or OVERRIDE.")
    public ResponseEntity<ApiResponse<MaterialIssueDetailResponse>> getMaterialIssue(@PathVariable UUID issueId) {
        return ResponseEntity.ok(ApiResponse.ok(materialIssueService.getDetail(issueId)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/material-issue-candidates")
    @Operation(summary = "Where each component can be issued from",
            description = "G8b: mode=NORMAL (default) lists the work order's own active reservations with a "
                    + "quick-fill amount; mode=SUPPLEMENTAL lists free, eligible stock in every warehouse of the plant.")
    public ResponseEntity<ApiResponse<PageResult<MaterialIssueCandidateResponse>>> materialIssueCandidates(
            @PathVariable UUID workOrderId,
            @RequestParam(required = false) UUID componentLineId,
            @RequestParam(defaultValue = "NORMAL") MaterialIssueCandidateService.Mode mode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(materialIssueCandidateService.candidates(
                workOrderId, componentLineId, mode, PageableFactory.of(page, size, "createdAt", "desc"))));
    }

    /**
     * Flat material issue history (spec §4.1). Plant-scoped, so it cross-checks {@code X-Plant-Id};
     * {@code workOrderId} narrows it and is optional.
     */
    @GetMapping("/v1/material-issues")
    @Operation(summary = "List material issue documents across a plant",
            description = "Spec §4.1. Flat form of "
                    + "GET /work-orders/{workOrderId}/material-issues; pass workOrderId to narrow it "
                    + "to one work order. status=PENDING_APPROVAL is the plant-wide Over-BOM approval "
                    + "queue. Default sort is requestedAt, which every document carries; postedAt is "
                    + "null until approval, so sorting the queue by it orders nothing.")
    public ResponseEntity<ApiResponse<PageResult<MaterialIssueResponse>>> listMaterialIssuesByPlant(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID workOrderId,
            @RequestParam(required = false) MaterialIssueStatus status,
            // G8b: issue code or work order number; plant-local days on requestedAt, both ends inclusive.
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "requestedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(materialIssueService.listByPlant(
                plantId, workOrderId, status, search, fromDate, toDate, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    /**
     * Flat material issue endpoint (spec §4.1). The multi-line
     * {@code POST /work-orders/{id}/material-issues} above is kept for the cases the shop-floor UI
     * does not cover — over-issue with a justification, several components in one document.
     */
    @PostMapping("/v1/material-issues")
    @Operation(summary = "Issue one reserved component",
            description = "Single-line form of POST /work-orders/{workOrderId}/material-issues. "
                    + "Consumes the given reservation; does not support over-issue override.")
    public ResponseEntity<ApiResponse<MaterialIssueResponse>> postFlatMaterialIssue(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MaterialIssueFlatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(materialIssueService.postFlat(request, idempotencyKey)));
    }

    @GetMapping("/v1/production-executions/candidates")
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
    @GetMapping("/v1/production-executions")
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

    @PostMapping("/v1/work-orders/{workOrderId}/production-executions")
    @Operation(summary = "Report shop floor production",
            description = "Records good/scrap/rework quantities. This — not the production receipt — "
                    + "is what advances and completes the work order. G8c: expectedWorkOrderVersion is "
                    + "required; on a work order with operations, workOrderOperationId is required, fresh work "
                    + "draws on the operation's confirmed input and a retry (sourceExecutionId) on its source's "
                    + "pending rework, only the last operation's good is final good, and consumption[] draws on "
                    + "the work order's own posted issue lines. Over a cap ⇒ 409 PLANNED_QUANTITY_EXCEEDED.")
    public ResponseEntity<ApiResponse<ProductionExecutionResponse>> reportProduction(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionExecutionPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(productionExecutionService.report(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/production-executions")
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

    @GetMapping("/v1/work-orders/{workOrderId}/operations")
    @Operation(summary = "Operations of a work order with their pools",
            description = "G8c: status, confirmed input, fresh remaining, good/scrap, historical and pending "
                    + "rework, transferred and transferable good. Input and caps are null when inputKnown is false.")
    public ResponseEntity<ApiResponse<List<WorkOrderOperationProgressResponse>>> listOperations(
            @PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(operationService.list(workOrderId)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/operations/{operationId}/input")
    @Operation(summary = "Confirm input of an operation",
            description = "G8c: the first operation takes input from the plan once material was issued "
                    + "(409 MATERIAL_ISSUE_REQUIRED); a later one takes a transfer of its finished predecessor's "
                    + "good (409 OPERATION_SEQUENCE_CONFLICT otherwise). Over the cap ⇒ 409 PLANNED_QUANTITY_EXCEEDED.")
    public ResponseEntity<ApiResponse<WorkOrderOperationProgressResponse>> recordOperationInput(
            @PathVariable UUID workOrderId,
            @PathVariable UUID operationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OperationInputRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                operationService.recordInput(workOrderId, operationId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/operations/{operationId}/finish")
    @Operation(summary = "Finish an operation",
            description = "G8c: allowed once its input and pending rework are used up and every good unit of the "
                    + "predecessor was transferred in (409 STATE_CONFLICT otherwise).")
    public ResponseEntity<ApiResponse<WorkOrderOperationProgressResponse>> finishOperation(
            @PathVariable UUID workOrderId,
            @PathVariable UUID operationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkOrderVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                operationService.finish(workOrderId, operationId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/complete-production")
    @Operation(summary = "Complete production of a work order",
            description = "G8c: for completing below the plan — reaching the plan completes it on its own. Every "
                    + "operation must be finished and no issue pending. A shortfall needs a 5–2000 character reason "
                    + "and PERM_MATERIAL_ISSUE_APPROVE. Nothing is received, closed or posted to stock.")
    public ResponseEntity<ApiResponse<ProductionCompletionResponse>> completeProduction(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionCompletionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                operationService.completeProduction(workOrderId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/wip-transactions")
    @Operation(summary = "Record WIP scrap or rework transaction")
    public ResponseEntity<ApiResponse<WipTransactionResponse>> recordWipTransaction(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody WipTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(wipTransactionService.record(workOrderId, request)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/wip-transactions")
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

    @GetMapping("/v1/production-receipts/candidates")
    @Operation(summary = "List work orders with finished output still to be warehoused",
            description = "Spec §6.2. Returns RELEASED, IN_PROGRESS or COMPLETED work orders whose "
                    + "receiptable good — min(planned, final good), G9a D12 — has not yet been fully receipted, "
                    + "already net of receipts still in DRAFT or PENDING_APPROVAL (invariant B76). COMPLETED is "
                    + "included on purpose: the shop floor being done does not mean the goods "
                    + "reached the racks. Optional workOrderId and free-text search (work order code, item SKU/name).")
    public ResponseEntity<ApiResponse<PageResult<ProductionReceiptCandidateResponse>>> listReceiptCandidates(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID workOrderId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "plannedEndAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.listCandidates(
                plantId, workOrderId, search, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    /** Flat plant-scoped receipt list (spec §6.2) with the G9a filters, all optional and combined with AND. */
    @GetMapping("/v1/production-receipts")
    @Operation(summary = "List production receipt documents across a plant",
            description = "Spec §6.2. Flat form of GET /work-orders/{workOrderId}/production-receipts. G9a filters: "
                    + "workOrderId, destinationWarehouseId, receivedFrom/receivedTo (plant-local dates, inclusive) and "
                    + "search (receipt code, work order code, item SKU/name, lot number). QC-only permissions read "
                    + "APPROVED receipts only.")
    public ResponseEntity<ApiResponse<PageResult<ProductionReceiptResponse>>> listProductionReceiptsByPlant(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(required = false) ProductionReceiptStatus status,
            @RequestParam(required = false) UUID workOrderId,
            @RequestParam(required = false) UUID destinationWarehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "postedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.listByPlant(
                plantId, status, workOrderId, destinationWarehouseId, receivedFrom, receivedTo, search,
                PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/production-receipts")
    @Operation(summary = "Create production receipt document",
            description = "Creates a DRAFT receipt. No stock movement is created and the work order "
                    + "completed quantity is unchanged until the receipt is approved. G9a: expectedWorkOrderVersion "
                    + "and receivedOn are required; the destination must be the work order's output warehouse; the "
                    + "quantity may not exceed min(planned, final good) − approved − open claims.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> postProductionReceipt(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionReceiptPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(productionReceiptService.post(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/production-receipts")
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

    @GetMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}")
    @Operation(summary = "Get one production receipt",
            description = "G9a. Versions, quota of the work order (G/C/A/D/W/U) and the caller's allowed actions.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> getProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId) {
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.get(workOrderId, receiptId)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/events")
    @Operation(summary = "Lifecycle history of one production receipt",
            description = "G9a. Created, submitted, withdrawn, approved, rejected, cancelled and QC steps, oldest first.")
    public ResponseEntity<ApiResponse<PageResult<ProductionReceiptEventResponse>>> listProductionReceiptEvents(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.events(
                workOrderId, receiptId, PageableFactory.of(page, size, "createdAt", "asc"))));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/source")
    @Operation(summary = "Where the output of one production receipt came from",
            description = "G9b. The final-good executions the receipt draws from (allocated by the server, oldest report "
                    + "first) and the work order's posted material with what was consumed and returned "
                    + "(materialAttribution WORK_ORDER_LEVEL). Receipts written before the contribution ledger report "
                    + "outputEvidenceKnown = false.")
    public ResponseEntity<ApiResponse<ProductionReceiptSourceResponse>> getProductionReceiptSource(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId) {
        return ResponseEntity.ok(ApiResponse.ok(productionReceiptService.source(workOrderId, receiptId)));
    }

    @PatchMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}")
    @Operation(summary = "Edit a draft production receipt",
            description = "G9a. Requester only. Quantity, lot number, dates and note; the source cannot change.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> updateProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionReceiptUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productionReceiptService.update(workOrderId, receiptId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/submit")
    @Operation(summary = "Submit draft production receipt for approval",
            description = "Moves a DRAFT receipt to PENDING_APPROVAL. Still no inventory impact. G9a: requester only, "
                    + "body {expectedVersion, expectedWorkOrderVersion}.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> submitProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionReceiptVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productionReceiptService.submit(workOrderId, receiptId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/withdraw")
    @Operation(summary = "Withdraw a pending production receipt back to draft",
            description = "G9a. Requester only, reason 5–2000 characters. The claimed output stays claimed.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> withdrawProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionReceiptReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productionReceiptService.withdraw(workOrderId, receiptId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/cancel")
    @Operation(summary = "Cancel a draft or pending production receipt",
            description = "G9a. Requester only, reason 5–2000 characters. Terminal; the claimed output is free again.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> cancelProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionReceiptReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productionReceiptService.cancel(workOrderId, receiptId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/approve")
    @Operation(summary = "Approve pending production receipt",
            description = "Creates the RECEIVE stock movements and advances the work order. "
                    + "Newly created output lots start in HOLD status. G9a: body {expectedVersion, "
                    + "expectedWorkOrderVersion}; source, quota, lot number and dates are checked again.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> approveProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionReceiptVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productionReceiptService.approve(workOrderId, receiptId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/reject")
    @Operation(summary = "Reject pending production receipt",
            description = "Closes the receipt without any inventory impact. G9a: both versions and a reason of "
                    + "5–2000 characters.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> rejectProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductionReceiptReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productionReceiptService.reject(workOrderId, receiptId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/qc-disposition")
    @Operation(summary = "Record QC disposition for an approved production receipt",
            description = "For lot-tracked output, changes the output lot from HOLD to AVAILABLE or "
                    + "REJECTED. For NON_TRACKED output, AVAILABLE releases qualityHoldQuantity while "
                    + "REJECTED leaves it held. On-hand quantity is unchanged either way.")
    public ResponseEntity<ApiResponse<ProductionReceiptResponse>> qcDispositionProductionReceipt(
            @PathVariable UUID workOrderId,
            @PathVariable UUID receiptId,
            @Valid @RequestBody ProductionReceiptQcDispositionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productionReceiptService.qcDisposition(workOrderId, receiptId, request)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/variance")
    @Operation(summary = "Get work order variance — material, output, WIP, and time",
            description = "Returns materialLines (planned vs issued per component, OVER_ISSUED / "
                    + "UNDER_ISSUED), outputVariance (planned vs actual output), wipSummary "
                    + "(scrap, rework) — invariant B20 — and timeVariance (plannedMinutes/"
                    + "actualMinutes/varianceMinutes, invariant B86). plannedMinutes sums "
                    + "setupMinutes + runMinutesPerUnit x plannedQuantity across the routing "
                    + "snapshot; actualMinutes sums the duration of every production execution "
                    + "that has ended, excluding ones still in progress. costVariance is retained "
                    + "for compatibility but authoritative=false while detailed costing is deferred.")
    public ResponseEntity<ApiResponse<WorkOrderVarianceResponse>> getVariance(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(varianceService.getVariance(workOrderId)));
    }

}
