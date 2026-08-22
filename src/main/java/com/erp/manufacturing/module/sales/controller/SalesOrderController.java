package com.erp.manufacturing.module.sales.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.PlanningDemandLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderUpdateRequest;
import com.erp.manufacturing.module.sales.service.SalesOrderService;
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
@RequestMapping("/sales-orders")
@RequiredArgsConstructor
@Tag(name = "Sales Orders", description = "Customer orders and the independent demand they generate")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private final PlantContextResolver plantContextResolver;

    @PostMapping("/v1")
    @Operation(summary = "Create sales order (DRAFT)")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> create(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @Valid @RequestBody SalesOrderCreateRequest request) {
        plantContextResolver.ensureMatches(plantHeader, request.plantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(salesOrderService.create(request)));
    }

    @GetMapping("/v1")
    @Operation(summary = "List sales orders")
    public ResponseEntity<ApiResponse<PageResult<SalesOrderResponse>>> list(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(search == null
                ? salesOrderService.list(
                        companyId, plantId, status,
                        PageableFactory.of(page, size, sortBy, sortDir, "salesOrderId"))
                : salesOrderService.list(
                        companyId, plantId, status, search,
                        PageableFactory.of(page, size, sortBy, sortDir, "salesOrderId"))));
    }

    /**
     * Declared before {@code /{salesOrderId}} would otherwise be considered — Spring matches the
     * literal path first, but keeping them adjacent makes the precedence obvious to readers.
     */
    @GetMapping("/v1/planning-demands")
    @Operation(summary = "List sales order lines selectable as planning demand")
    public ResponseEntity<ApiResponse<List<PlanningDemandLineResponse>>> planningDemands(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate horizonEnd) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.planningDemands(plantId, horizonEnd)));
    }

    @GetMapping("/v1/{salesOrderId}")
    @Operation(summary = "Get sales order")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> get(@PathVariable UUID salesOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.get(salesOrderId)));
    }

    @PatchMapping("/v1/{salesOrderId}")
    @Operation(summary = "Update a DRAFT sales order (full-replace lines)")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> update(
            @PathVariable UUID salesOrderId,
            @Valid @RequestBody SalesOrderUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.update(salesOrderId, request)));
    }

    @PostMapping("/v1/{salesOrderId}/confirm")
    @Operation(summary = "Confirm sales order and generate planning demand")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> confirm(@PathVariable UUID salesOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.confirm(salesOrderId)));
    }

    @PostMapping("/v1/{salesOrderId}/cancel")
    @Operation(summary = "Cancel sales order and its open planning demand")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> cancel(@PathVariable UUID salesOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.cancel(salesOrderId)));
    }
}
