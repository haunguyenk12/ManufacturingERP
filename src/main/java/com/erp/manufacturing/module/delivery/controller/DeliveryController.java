package com.erp.manufacturing.module.delivery.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.delivery.domain.DeliveryStatus;
import com.erp.manufacturing.module.delivery.dto.DeliveryCancelRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryCandidatesResponse;
import com.erp.manufacturing.module.delivery.dto.DeliveryCreateRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryEventResponse;
import com.erp.manufacturing.module.delivery.dto.DeliveryPostRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryResponse;
import com.erp.manufacturing.module.delivery.dto.DeliverySourceResponse;
import com.erp.manufacturing.module.delivery.dto.DeliveryUpdateRequest;
import com.erp.manufacturing.module.delivery.service.DeliveryQueryService;
import com.erp.manufacturing.module.delivery.service.DeliveryService;
import com.erp.manufacturing.module.sales.dto.CommercialCommandResult;
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
 * G10b deliveries. Every command needs {@code Idempotency-Key} and answers with a {@link CommercialCommandResult}; the
 * client re-reads the detail (FE Q25). The two order-scoped reads live under {@code /sales-orders/v1/...}, the root
 * that already owns sales orders (FE Q2).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Deliveries", description = "Dispatch of a sales order's goods to its customer (G10b)")
public class DeliveryController {

    private static final String KEY = "Idempotency-Key";

    private final DeliveryService deliveryService;
    private final DeliveryQueryService queryService;
    private final PlantContextResolver plantContextResolver;

    @GetMapping("/v1/deliveries")
    @Operation(summary = "List deliveries of a plant")
    public ResponseEntity<ApiResponse<PageResult<DeliveryResponse>>> list(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) UUID salesOrderId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(queryService.list(plantId, status, salesOrderId, customerId,
                warehouseId, fromDate, toDate, search,
                PageableFactory.of(page, size, "documentDate", "desc", "deliveryId"))));
    }

    @GetMapping("/v1/deliveries/related")
    @Operation(summary = "POSTED deliveries of a plant that took stock from a lot")
    public ResponseEntity<ApiResponse<PageResult<DeliveryResponse>>> related(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam UUID lotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(queryService.related(plantId, lotId,
                PageableFactory.of(page, size, "postedAt", "desc", "deliveryId"))));
    }

    @GetMapping("/v1/deliveries/{deliveryId}")
    @Operation(summary = "Get a delivery with its lines, sources and movements")
    public ResponseEntity<ApiResponse<DeliveryResponse>> get(@PathVariable UUID deliveryId) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.get(deliveryId)));
    }

    @GetMapping("/v1/deliveries/{deliveryId}/history")
    @Operation(summary = "History of a delivery")
    public ResponseEntity<ApiResponse<PageResult<DeliveryEventResponse>>> history(
            @PathVariable UUID deliveryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.history(deliveryId, PageableFactory.unsorted(page, size))));
    }

    @PostMapping("/v1/deliveries")
    @Operation(summary = "Create a DRAFT delivery for one sales order (claims quota, moves no stock)")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> create(
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DeliveryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(deliveryService.create(request, idempotencyKey)));
    }

    @PatchMapping("/v1/deliveries/{deliveryId}")
    @Operation(summary = "Replace a DRAFT delivery's date, note and lines")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> update(
            @PathVariable UUID deliveryId,
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DeliveryUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.update(deliveryId, request, idempotencyKey)));
    }

    @PostMapping("/v1/deliveries/{deliveryId}/post")
    @Operation(summary = "Post a delivery: issue its stock, own holds first")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> post(
            @PathVariable UUID deliveryId,
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DeliveryPostRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.post(deliveryId, request, idempotencyKey)));
    }

    @PostMapping("/v1/deliveries/{deliveryId}/cancel")
    @Operation(summary = "Cancel a DRAFT delivery (reason required)")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> cancel(
            @PathVariable UUID deliveryId,
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DeliveryCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.cancel(deliveryId, request, idempotencyKey)));
    }

    @GetMapping("/sales-orders/v1/{salesOrderId}/delivery-candidates")
    @Operation(summary = "Lines of a sales order with delivered, claimed and still claimable quantities")
    public ResponseEntity<ApiResponse<DeliveryCandidatesResponse>> candidates(
            @PathVariable UUID salesOrderId,
            @RequestParam(required = false) UUID editingDeliveryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.candidates(salesOrderId, editingDeliveryId,
                PageableFactory.unsorted(page, size))));
    }

    @GetMapping("/sales-orders/v1/{salesOrderId}/lines/{salesOrderLineId}/delivery-sources")
    @Operation(summary = "Stock positions a sales order line can be delivered from")
    public ResponseEntity<ApiResponse<PageResult<DeliverySourceResponse>>> sources(
            @PathVariable UUID salesOrderId,
            @PathVariable UUID salesOrderLineId,
            @RequestParam(required = false) UUID editingDeliveryId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.sources(salesOrderId, salesOrderLineId, editingDeliveryId,
                warehouseId, PageableFactory.unsorted(page, size))));
    }
}
