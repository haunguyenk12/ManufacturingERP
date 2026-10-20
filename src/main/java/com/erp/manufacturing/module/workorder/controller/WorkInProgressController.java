package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnPostRequest;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipMaterialLineResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipSection;
import com.erp.manufacturing.module.workorder.dto.wip.WipWorkOrderSummaryResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WorkOrderWipResponse;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReturnService;
import com.erp.manufacturing.module.workorder.service.execution.WorkOrderWipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * G8d: work in progress and material returns. The two plant lists name the plant and cross-check {@code X-Plant-Id}
 * ({@code error-handling.md §5.6.1}); the endpoints identified by a work order or a return do not — the id is the scope.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Work in progress", description = "WIP read model and material returns (G8d)")
public class WorkInProgressController {

    private final WorkOrderWipService wipService;
    private final MaterialReturnService materialReturnService;
    private final PlantContextResolver plantContextResolver;

    @GetMapping("/v1/plants/{plantId}/wip")
    @Operation(summary = "List the work in progress of a plant",
            description = "One row per work order. Without status: RELEASED, IN_PROGRESS and COMPLETED. search matches "
                    + "the work order number or product SKU; fromDate/toDate are plant-local days on createdAt, both "
                    + "inclusive. Needs PERM_WIP_READ.")
    public ResponseEntity<ApiResponse<PageResult<WipWorkOrderSummaryResponse>>> listWip(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @PathVariable UUID plantId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID workOrderId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(wipService.listByPlant(plantId, status, workOrderId, search, fromDate,
                toDate, PageableFactory.of(page, size, sortBy, sortDir, "workOrderId"))));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/wip")
    @Operation(summary = "Work in progress of one work order",
            description = "section = MATERIALS | OPERATIONS | REWORK | AWAITING_RECEIPT; omitted returns every section. "
                    + "Quantities only. Needs PERM_WIP_READ.")
    public ResponseEntity<ApiResponse<WorkOrderWipResponse>> getWip(
            @PathVariable UUID workOrderId,
            @RequestParam(required = false) WipSection section) {
        return ResponseEntity.ok(ApiResponse.ok(wipService.get(workOrderId, section)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/material-returns")
    @Operation(summary = "Return unused issued material to its source",
            description = "Body {expectedWorkOrderVersion, reason (5-2000), lines[1..500] {sourceIssueLineId, quantity}}. "
                    + "Each line goes back to the warehouse and lot of its issue line. Cap per issue line: issued - "
                    + "consumed - returned. Idempotency-Key required. Needs PERM_MATERIAL_RETURN_MANAGE.")
    public ResponseEntity<ApiResponse<MaterialReturnResponse>> postReturn(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MaterialReturnPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(materialReturnService.post(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/material-return-candidates")
    @Operation(summary = "Issue lines of a work order that still hold unused material",
            description = "Needs PERM_MATERIAL_RETURN_MANAGE.")
    public ResponseEntity<ApiResponse<PageResult<WipMaterialLineResponse>>> listReturnCandidates(
            @PathVariable UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(materialReturnService.candidates(
                workOrderId, PageableFactory.unsorted(page, size))));
    }

    @GetMapping("/v1/material-returns")
    @Operation(summary = "List material returns across a plant",
            description = "search matches the return code or the work order number; fromDate/toDate are plant-local "
                    + "days on postedAt, both inclusive. Needs PERM_MATERIAL_RETURN_READ or _MANAGE.")
    public ResponseEntity<ApiResponse<PageResult<MaterialReturnResponse>>> listReturns(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID workOrderId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "postedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(materialReturnService.listByPlant(plantId, workOrderId, search, fromDate,
                toDate, PageableFactory.of(page, size, sortBy, sortDir, "materialReturnId"))));
    }

    @GetMapping("/v1/material-returns/{materialReturnId}")
    @Operation(summary = "One material return with its lines",
            description = "Needs PERM_MATERIAL_RETURN_READ or _MANAGE on the work order's plant.")
    public ResponseEntity<ApiResponse<MaterialReturnResponse>> getReturn(@PathVariable UUID materialReturnId) {
        return ResponseEntity.ok(ApiResponse.ok(materialReturnService.get(materialReturnId)));
    }
}
