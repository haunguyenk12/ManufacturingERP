package com.erp.manufacturing.module.planning.controller;

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

@RestController
@RequiredArgsConstructor
@Tag(name = "MRP", description = "MRP runs, requirements, and supply suggestions")
public class MrpController {

    private final MrpRunService mrpRunService;
    private final SupplySuggestionService supplySuggestionService;

    @PostMapping("/api/v1/mrp/runs")
    @Operation(summary = "Run MRP synchronously")
    public ResponseEntity<ApiResponse<MrpRunResponse>> run(@Valid @RequestBody MrpRunCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mrpRunService.run(request)));
    }

    @GetMapping("/api/v1/mrp/runs")
    @Operation(summary = "List MRP runs")
    public ResponseEntity<ApiResponse<PageResult<MrpRunResponse>>> listRuns(
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) MrpRunStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.list(
                companyId, plantId, warehouseId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/mrp/runs/{runId}")
    @Operation(summary = "Get MRP run")
    public ResponseEntity<ApiResponse<MrpRunResponse>> getRun(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.get(runId)));
    }

    @GetMapping("/api/v1/mrp/runs/{runId}/requirements")
    @Operation(summary = "List MRP requirement lines")
    public ResponseEntity<ApiResponse<PageResult<MrpRequirementLineResponse>>> listRequirements(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "requirementLevel") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.listRequirements(
                runId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/mrp/runs/{runId}/suggestions")
    @Operation(summary = "List MRP supply suggestions")
    public ResponseEntity<ApiResponse<PageResult<SupplySuggestionResponse>>> listSuggestions(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "neededByDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(mrpRunService.listSuggestions(
                runId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/api/v1/mrp/suggestions/{suggestionId}/approve")
    @Operation(summary = "Approve supply suggestion")
    public ResponseEntity<ApiResponse<SupplySuggestionResponse>> approveSuggestion(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) SupplySuggestionDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplySuggestionService.approve(suggestionId, request)));
    }

    @PostMapping("/api/v1/mrp/suggestions/{suggestionId}/reject")
    @Operation(summary = "Reject supply suggestion")
    public ResponseEntity<ApiResponse<SupplySuggestionResponse>> rejectSuggestion(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) SupplySuggestionDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplySuggestionService.reject(suggestionId, request)));
    }

    @PostMapping("/api/v1/mrp/suggestions/{suggestionId}/convert-to-work-order")
    @Operation(summary = "Convert approved WORK_ORDER suggestion to work order")
    public ResponseEntity<ApiResponse<SupplySuggestionResponse>> convertSuggestionToWorkOrder(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) SupplySuggestionConvertWorkOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                supplySuggestionService.convertToWorkOrder(suggestionId, request)));
    }

}
