package com.erp.manufacturing.module.billing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCancelRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCandidatesResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCreateRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceEventResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePostRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceUpdateRequest;
import com.erp.manufacturing.module.billing.service.SalesInvoiceQueryService;
import com.erp.manufacturing.module.billing.service.SalesInvoiceService;
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
 * G10c sales invoices. Every command needs {@code Idempotency-Key} and answers with a {@link CommercialCommandResult};
 * the client re-reads the detail (FE Q25). Preview saves nothing. The order-scoped candidates live under
 * {@code /sales-orders/v1/...} (FE Q2).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sales invoices", description = "Internal invoices of a sales order's posted deliveries (G10c)")
public class SalesInvoiceController {

    private static final String KEY = "Idempotency-Key";

    private final SalesInvoiceService invoiceService;
    private final SalesInvoiceQueryService queryService;
    private final PlantContextResolver plantContextResolver;

    @GetMapping("/v1/sales-invoices")
    @Operation(summary = "List sales invoices of a plant")
    public ResponseEntity<ApiResponse<PageResult<SalesInvoiceResponse>>> list(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(required = false) SalesInvoiceStatus status,
            @RequestParam(required = false) UUID salesOrderId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(queryService.list(plantId, status, salesOrderId, customerId,
                fromDate, toDate, search, PageableFactory.of(page, size, "documentDate", "desc", "invoiceId"))));
    }

    @GetMapping("/v1/sales-invoices/{invoiceId}")
    @Operation(summary = "Get a sales invoice with its lines")
    public ResponseEntity<ApiResponse<SalesInvoiceResponse>> get(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.get(invoiceId)));
    }

    @GetMapping("/v1/sales-invoices/{invoiceId}/history")
    @Operation(summary = "History of a sales invoice")
    public ResponseEntity<ApiResponse<PageResult<SalesInvoiceEventResponse>>> history(
            @PathVariable UUID invoiceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.history(invoiceId, PageableFactory.unsorted(page, size))));
    }

    @PostMapping("/v1/sales-invoices/preview")
    @Operation(summary = "Price invoice lines on today's billing base (saves and holds nothing)")
    public ResponseEntity<ApiResponse<SalesInvoicePreviewResponse>> preview(
            @Valid @RequestBody SalesInvoicePreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.preview(request)));
    }

    @PostMapping("/v1/sales-invoices")
    @Operation(summary = "Create a DRAFT invoice for one sales order (claims delivered quantity, no receivable yet)")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> create(
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SalesInvoiceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(invoiceService.create(request, idempotencyKey)));
    }

    @PatchMapping("/v1/sales-invoices/{invoiceId}")
    @Operation(summary = "Replace a DRAFT invoice's dates, note and lines")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> update(
            @PathVariable UUID invoiceId,
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SalesInvoiceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.update(invoiceId, request, idempotencyKey)));
    }

    @PostMapping("/v1/sales-invoices/{invoiceId}/post")
    @Operation(summary = "Post an invoice: it becomes a receivable (no stock effect)")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> post(
            @PathVariable UUID invoiceId,
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SalesInvoicePostRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.post(invoiceId, request, idempotencyKey)));
    }

    @PostMapping("/v1/sales-invoices/{invoiceId}/cancel")
    @Operation(summary = "Cancel a DRAFT invoice (reason required)")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> cancel(
            @PathVariable UUID invoiceId,
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SalesInvoiceCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.cancel(invoiceId, request, idempotencyKey)));
    }

    @GetMapping("/sales-orders/v1/{salesOrderId}/invoice-candidates")
    @Operation(summary = "Lines of a sales order's posted deliveries with invoiced, claimed and billable quantities")
    public ResponseEntity<ApiResponse<SalesInvoiceCandidatesResponse>> candidates(
            @PathVariable UUID salesOrderId,
            @RequestParam(required = false) UUID editingInvoiceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.candidates(salesOrderId, editingInvoiceId,
                PageableFactory.unsorted(page, size))));
    }
}
