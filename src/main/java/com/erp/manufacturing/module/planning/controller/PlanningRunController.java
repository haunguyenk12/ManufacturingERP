package com.erp.manufacturing.module.planning.controller;

import com.erp.manufacturing.common.web.PlantContextResolver;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.planning.domain.MrpRunStatus;
import com.erp.manufacturing.module.planning.dto.*;
import com.erp.manufacturing.module.planning.service.MrpRunService;
import com.erp.manufacturing.module.planning.service.PlanningConversionService;
import com.erp.manufacturing.module.planning.service.SupplySuggestionService;
import com.erp.manufacturing.common.web.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Planning runs and the supply suggestions they produce.
 *
 * <p>F5-B renamed {@code /api/v1/mrp/**} to the spec §2.2 vocabulary. Runs keep their sub-resources
 * nested; suggestion actions sit on a flat {@code /api/v1/supply-suggestions/{id}} instead of the
 * spec's {@code /planning-runs/{id}/proposals/{proposalId}} because the run id is not needed to
 * identify a suggestion, and purchasing hangs its own convert action off the same resource
 * ({@code PurchaseRequisitionController}). Endpoint names are the negotiable part of the spec;
 * field names and business rules are not (CLAUDE.md §0.1).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Planning Runs", description = "MRP planning runs, requirements, and supply suggestions")
public class PlanningRunController {

    private final MrpRunService mrpRunService;
    private final SupplySuggestionService supplySuggestionService;
    private final PlanningConversionService planningConversionService;
    private final PlantContextResolver plantContextResolver;

    @PostMapping("/v1/planning-runs")
    @Operation(summary = "Run MRP synchronously",
            description = """
                    Optional Idempotency-Key: replaying the same key with the same body returns the
                    run created the first time instead of computing a second one over the same
                    demand. Replaying it with a different body is 409 IDEMPOTENCY_CONFLICT. Sending
                    no key keeps the previous behaviour — every call starts a new run.

                    A run that ended FAILED still owns its key; retry it with a new key.""")
    public ResponseEntity<ApiResponse<MrpRunResponse>> run(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MrpRunCreateRequest request) {
        plantContextResolver.ensureMatches(plantHeader, request.plantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mrpRunService.run(request, idempotencyKey)));
    }

    @PostMapping("/v1/planning-runs/commercial")
    @Operation(summary = "Run MRP over sales order lines (commercial run)",
            description = """
                    Plans the uncovered quantity of the selected sales order lines (ordered minus QC
                    accepted, held stock and open work order coverage). salesOrderLineIds are sales
                    order line ids, not planning demand ids. Period at most 90 days, both ends
                    included. itemWarehouseMappings choose the netting warehouse of an item for this
                    run only; outputWarehouseId, when sent, must equal demandWarehouseId.
                    includeRop=true is refused (422): no reorder quantity is configured anywhere.
                    Optional Idempotency-Key, same contract as POST /v1/planning-runs.""")
    public ResponseEntity<ApiResponse<MrpRunResponse>> runCommercial(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CommercialPlanningRunRequest request) {
        plantContextResolver.ensureMatches(plantHeader, request.plantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mrpRunService.runCommercial(request, idempotencyKey)));
    }

    @GetMapping("/v1/planning-runs")
    @Operation(summary = "List planning runs")
    public ResponseEntity<ApiResponse<PageResult<MrpRunResponse>>> listRuns(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) MrpRunStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.list(
                companyId, plantId, warehouseId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/planning-runs/{runId}")
    @Operation(summary = "Get planning run")
    public ResponseEntity<ApiResponse<MrpRunResponse>> getRun(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.get(runId)));
    }

    @GetMapping("/v1/planning-runs/{runId}/requirements")
    @Operation(summary = "List planning run requirement lines")
    public ResponseEntity<ApiResponse<PageResult<MrpRequirementLineResponse>>> listRequirements(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "requirementLevel") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.listRequirements(
                runId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/planning-runs/{runId}/requirements/{requirementLineId}")
    @Operation(summary = "Get one requirement line with its proposal and conversions")
    public ResponseEntity<ApiResponse<MrpRequirementLineResponse>> getRequirement(
            @PathVariable UUID runId,
            @PathVariable UUID requirementLineId) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.getRequirement(runId, requirementLineId)));
    }

    @PostMapping("/v1/planning-runs/{runId}/conversions")
    @Operation(summary = "Convert proposals of a commercial run into draft documents",
            description = """
                    All entries MAKE (one DRAFT work order each) or all BUY (one DRAFT purchase
                    order, supplierId required, one receiving warehouse). Partial and repeatable:
                    each quantity must not exceed the requirement's remaining convertible quantity.
                    expectedConversionVersion / expectedSourceVersion come from the run; a stale
                    one is 409 CONCURRENT_MODIFICATION / PLANNING_SOURCE_STALE. No approve step.
                    Optional Idempotency-Key: a replay returns the same documents.""")
    public ResponseEntity<ApiResponse<PlanningConversionResult>> convert(
            @PathVariable UUID runId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlanningConversionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(planningConversionService.convert(runId, request, idempotencyKey)));
    }

    @GetMapping("/v1/planning-runs/{runId}/conversions")
    @Operation(summary = "List conversions of a planning run")
    public ResponseEntity<ApiResponse<PageResult<PlanningConversionResponse>>> listConversions(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(planningConversionService.list(
                runId, PageableFactory.of(page, size, "createdAt", "asc"))));
    }

    /**
     * CF-4: goods receipt lines that held pending purchase-QC quantity when the run ran, as the run captured them.
     * Stable order (receipt code, line id) — the query's own order is the contract, so no sort parameter.
     */
    @GetMapping("/v1/planning-runs/{runId}/pending-qc-supplies")
    @Operation(summary = "List purchase supply awaiting QC captured by a planning run")
    public ResponseEntity<ApiResponse<PageResult<PendingQcSupplyResponse>>> listPendingQcSupplies(
            @PathVariable UUID runId,
            @RequestParam(required = false) UUID requirementId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.listPendingQcSupplies(
                runId, requirementId, warehouseId, PageableFactory.unsorted(page, size))));
    }

    @GetMapping("/v1/planning-runs/{runId}/suggestions")
    @Operation(summary = "List planning run supply suggestions")
    public ResponseEntity<ApiResponse<PageResult<SupplySuggestionResponse>>> listSuggestions(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "neededByDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.listSuggestions(
                runId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/v1/supply-suggestions/{suggestionId}/approve")
    @Operation(summary = "Approve supply suggestion")
    public ResponseEntity<ApiResponse<SupplySuggestionResponse>> approveSuggestion(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) SupplySuggestionDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplySuggestionService.approve(suggestionId, request)));
    }

    @PostMapping("/v1/supply-suggestions/{suggestionId}/reject")
    @Operation(summary = "Reject supply suggestion")
    public ResponseEntity<ApiResponse<SupplySuggestionResponse>> rejectSuggestion(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) SupplySuggestionDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplySuggestionService.reject(suggestionId, request)));
    }

    @PostMapping("/v1/supply-suggestions/{suggestionId}/convert-to-work-order")
    @Operation(summary = "Convert approved MAKE suggestion to work order")
    public ResponseEntity<ApiResponse<SupplySuggestionResponse>> convertSuggestionToWorkOrder(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) SupplySuggestionConvertWorkOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                supplySuggestionService.convertToWorkOrder(suggestionId, request)));
    }

}
