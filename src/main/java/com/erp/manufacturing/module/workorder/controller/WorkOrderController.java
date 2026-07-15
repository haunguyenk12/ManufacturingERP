package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.service.WorkOrderService;
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

    @PostMapping("/api/v1/plants/{plantId}/work-orders")
    @Operation(summary = "Create work order under plant")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> create(
            @PathVariable UUID plantId,
            @Valid @RequestBody WorkOrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(workOrderService.create(plantId, request)));
    }

    @GetMapping("/api/v1/plants/{plantId}/work-orders")
    @Operation(summary = "List work orders by plant")
    public ResponseEntity<ApiResponse<PageResult<WorkOrderResponse>>> list(
            @PathVariable UUID plantId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID productItemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.list(
                plantId, status, productItemId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/work-orders/{workOrderId}")
    @Operation(summary = "Get work order")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> get(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.get(workOrderId)));
    }

    @PatchMapping("/api/v1/work-orders/{workOrderId}")
    @Operation(summary = "Update draft work order")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> update(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody WorkOrderUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.update(workOrderId, request)));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/release")
    @Operation(summary = "Release work order")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> release(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.release(workOrderId)));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/cancel")
    @Operation(summary = "Cancel work order")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> cancel(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.cancel(workOrderId)));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/component-issues")
    @Operation(summary = "Issue work order component")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> issueComponent(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkOrderComponentIssueRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.issueComponent(workOrderId, request, idempotencyKey)));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/output-completions")
    @Operation(summary = "Complete work order output")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> completeOutput(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkOrderOutputCompletionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workOrderService.completeOutput(workOrderId, request, idempotencyKey)));
    }

}
