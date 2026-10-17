package com.erp.manufacturing.module.sales.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.PlanningDemandLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderAllocationCandidatesResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderCancelRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftUpdateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationReleaseRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationResult;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderUpdateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderVersionRequest;
import com.erp.manufacturing.module.sales.service.SalesOrderReservationService;
import com.erp.manufacturing.module.sales.service.SalesOrderService;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
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
    private final SalesOrderReservationService reservationService;
    private final PlantContextResolver plantContextResolver;

    @PostMapping("/v1")
    @Operation(summary = "Create sales order (DRAFT) with a client-chosen order number and free-text customer")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> create(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @Valid @RequestBody SalesOrderCreateRequest request) {
        plantContextResolver.ensureMatches(plantHeader, request.plantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(salesOrderService.create(request)));
    }

    @PostMapping("/v1/drafts")
    @Operation(summary = "Create a DRAFT linked to a customer; the server generates the order number (G6a)")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> createDraft(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @Valid @RequestBody SalesOrderDraftCreateRequest request) {
        plantContextResolver.ensureMatches(plantHeader, request.plantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(salesOrderService.createDraft(request)));
    }

    @GetMapping("/v1")
    @Operation(summary = "List sales orders (search, status, order/due date ranges)")
    public ResponseEntity<ApiResponse<PageResult<SalesOrderResponse>>> list(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.list(
                companyId, plantId, status, search, orderDateFrom, orderDateTo, dueDateFrom, dueDateTo,
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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate horizonEnd,
            @RequestParam(defaultValue = "false") boolean uncoveredOnly) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.planningDemands(plantId, horizonEnd, uncoveredOnly)));
    }

    @GetMapping("/v1/{salesOrderId}")
    @Operation(summary = "Get sales order with line progress figures and linked documents")
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

    @PatchMapping("/v1/{salesOrderId}/draft")
    @Operation(summary = "Replace a DRAFT linked to a customer — header and every line (G6a)")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> updateDraft(
            @PathVariable UUID salesOrderId,
            @Valid @RequestBody SalesOrderDraftUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.updateDraft(salesOrderId, request)));
    }

    @PostMapping("/v1/{salesOrderId}/confirm")
    @Operation(summary = "Confirm sales order and generate planning demand (optional body: expectedVersion)")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> confirm(
            @PathVariable UUID salesOrderId,
            @Valid @RequestBody(required = false) SalesOrderVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.confirm(
                salesOrderId, request != null ? request.expectedVersion() : null)));
    }

    @PostMapping("/v1/{salesOrderId}/cancel")
    @Operation(summary = "Cancel sales order, its open planning demand and its stock holds (optional body: expectedVersion, reason)")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> cancel(
            @PathVariable UUID salesOrderId,
            @Valid @RequestBody(required = false) SalesOrderCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.cancel(salesOrderId, request)));
    }

    @GetMapping("/v1/{salesOrderId}/reservations")
    @Operation(summary = "List stock holds of a sales order (G6a)")
    public ResponseEntity<ApiResponse<PageResult<SalesOrderReservationResponse>>> listReservations(
            @PathVariable UUID salesOrderId,
            @RequestParam(required = false) UUID lineId,
            @RequestParam(required = false) MaterialReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.list(
                salesOrderId, lineId, status, PageableFactory.of(page, size, "createdAt", "desc", "reservationId"))));
    }

    @GetMapping("/v1/{salesOrderId}/lines/{lineId}/allocation-candidates")
    @Operation(summary = "Stock positions a line could be held from (G6a)")
    public ResponseEntity<ApiResponse<SalesOrderAllocationCandidatesResponse>> allocationCandidates(
            @PathVariable UUID salesOrderId,
            @PathVariable UUID lineId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.candidates(
                salesOrderId, lineId, warehouseId, PageableFactory.unsorted(page, size))));
    }

    @PostMapping("/v1/{salesOrderId}/reservations")
    @Operation(summary = "Hold stock for a sales order line (G6a)")
    public ResponseEntity<ApiResponse<SalesOrderReservationResult>> reserve(
            @PathVariable UUID salesOrderId,
            @Valid @RequestBody SalesOrderReservationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reservationService.reserve(salesOrderId, request)));
    }

    @PostMapping("/v1/{salesOrderId}/reservations/{reservationId}/release")
    @Operation(summary = "Release one stock hold of a sales order (G6a)")
    public ResponseEntity<ApiResponse<SalesOrderReservationResult>> release(
            @PathVariable UUID salesOrderId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody SalesOrderReservationReleaseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.release(salesOrderId, reservationId, request)));
    }
}
