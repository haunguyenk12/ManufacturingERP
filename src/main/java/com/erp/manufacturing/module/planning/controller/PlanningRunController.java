package com.erp.manufacturing.module.planning.controller;

import com.erp.manufacturing.common.web.PlantContextResolver;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.planning.domain.MrpRunStatus;
import com.erp.manufacturing.module.planning.dto.*;
import com.erp.manufacturing.module.planning.service.MrpRunService;
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
    private final PlantContextResolver plantContextResolver;

    @PostMapping("/api/v1/planning-runs")
    @Operation(summary = "Run MRP synchronously")
    public ResponseEntity<ApiResponse<MrpRunResponse>> run(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @Valid @RequestBody MrpRunCreateRequest request) {
        plantContextResolver.ensureMatches(plantHeader, request.plantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mrpRunService.run(request)));
    }

    @GetMapping("/api/v1/planning-runs")
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

    @GetMapping("/api/v1/planning-runs/{runId}")
    @Operation(summary = "Get planning run")
    public ResponseEntity<ApiResponse<MrpRunResponse>> getRun(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.get(runId)));
    }

    @GetMapping("/api/v1/planning-runs/{runId}/requirements")
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

    @GetMapping("/api/v1/planning-runs/{runId}/suggestions")
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

    @PostMapping("/api/v1/supply-suggestions/{suggestionId}/approve")
    @Operation(summary = "Approve supply suggestion")
    public ResponseEntity<ApiResponse<SupplySuggestionResponse>> approveSuggestion(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) SupplySuggestionDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplySuggestionService.approve(suggestionId, request)));
    }

    @PostMapping("/api/v1/supply-suggestions/{suggestionId}/reject")
    @Operation(summary = "Reject supply suggestion")
    public ResponseEntity<ApiResponse<SupplySuggestionResponse>> rejectSuggestion(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) SupplySuggestionDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplySuggestionService.reject(suggestionId, request)));
    }

    @PostMapping("/api/v1/supply-suggestions/{suggestionId}/convert-to-work-order")
    @Operation(summary = "Convert approved MAKE suggestion to work order")
    public ResponseEntity<ApiResponse<SupplySuggestionResponse>> convertSuggestionToWorkOrder(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) SupplySuggestionConvertWorkOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                supplySuggestionService.convertToWorkOrder(suggestionId, request)));
    }

}
