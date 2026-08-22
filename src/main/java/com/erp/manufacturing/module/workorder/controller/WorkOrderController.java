package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.web.PlantContextResolver;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.service.WorkOrderService;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderReadinessService;
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
@Tag(name = "Work Orders", description = "Production work order execution")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final WorkOrderReadinessService readinessService;
    private final PlantContextResolver plantContextResolver;

    @PostMapping("/v1/plants/{plantId}/work-orders")
    @Operation(summary = "Create work order under plant")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> create(
            @PathVariable UUID plantId,
            @Valid @RequestBody WorkOrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(workOrderService.create(plantId, request)));
    }

    @GetMapping("/v1/plants/{plantId}/work-orders")
    @Operation(summary = "List work orders by plant")
    public ResponseEntity<ApiResponse<PageResult<WorkOrderResponse>>> list(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @PathVariable UUID plantId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID productItemId,
            // Free-text over work order number and product SKU (spec §3.2).
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.list(
                plantId, status, productItemId, search, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/work-orders/{workOrderId}")
    @Operation(summary = "Get work order")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> get(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.get(workOrderId)));
    }

    @PatchMapping("/v1/work-orders/{workOrderId}")
    @Operation(summary = "Update draft work order")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> update(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody WorkOrderUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.update(workOrderId, request)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/material-readiness")
    @Operation(summary = "Get work order material readiness",
            description = "Shows reserved vs required quantity per component and the shortage that "
                    + "would block release. Not paginated: a work order has a small, bounded number "
                    + "of component lines.")
    public ResponseEntity<ApiResponse<WorkOrderMaterialReadinessResponse>> getMaterialReadiness(
            @PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(readinessService.getMaterialReadiness(workOrderId)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/plan")
    @Operation(summary = "Schedule draft work order",
            description = "Moves DRAFT to PLANNED. Nothing is reserved and no material moves — this "
                    + "only records that a planner has committed to the dates.")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> plan(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.plan(workOrderId)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/release")
    @Operation(summary = "Release work order",
            description = "Fails with 422 when material reservation does not fully cover the component "
                    + "requirements; the work order is then persisted as BLOCKED. Use "
                    + "GET /material-readiness to see the shortage per component.")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> release(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.release(workOrderId)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/cancel")
    @Operation(summary = "Cancel work order",
            description = "Spec §3.2 requires a reason; omitting it returns 400 APPROVAL_REASON_REQUIRED.")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> cancel(
            @PathVariable UUID workOrderId,
            @RequestBody(required = false) WorkOrderCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.cancel(workOrderId, request)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/close")
    @Operation(summary = "Close work order",
            description = "Reconciles and permanently locks a COMPLETED work order — releases any "
                    + "leftover ACTIVE reservation back to available stock, then locks it against "
                    + "every further write (reserve, issue, receipt, adjust). Only allowed from "
                    + "COMPLETED; anything else returns 409 STATE_CONFLICT.")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> close(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.close(workOrderId)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/component-issues")
    @Operation(summary = "Issue work order component",
            description = "Convenience endpoint for a single component line. It does NOT support "
                    + "over-issue override: issuing beyond the remaining BOM requirement fails here. "
                    + "Use POST /api/v1/work-orders/{workOrderId}/material-issues with an "
                    + "overrideReason instead.")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> issueComponent(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkOrderComponentIssueRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.issueComponent(workOrderId, request, idempotencyKey)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/output-completions")
    @Operation(summary = "Submit work order output for approval",
            description = "Creates a PENDING_APPROVAL production receipt. The work order is not "
                    + "completed and no stock is created until the receipt is approved.")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> completeOutput(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkOrderOutputCompletionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.completeOutput(workOrderId, request, idempotencyKey)));
    }

}
