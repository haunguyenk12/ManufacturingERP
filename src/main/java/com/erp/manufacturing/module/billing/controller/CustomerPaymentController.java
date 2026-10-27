package com.erp.manufacturing.module.billing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus;
import com.erp.manufacturing.module.billing.domain.PaymentMethod;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentCancelRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentCreateRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentEventResponse;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentPostRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentResponse;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentUpdateRequest;
import com.erp.manufacturing.module.billing.dto.PaymentInvoiceCandidateResponse;
import com.erp.manufacturing.module.billing.service.CustomerPaymentQueryService;
import com.erp.manufacturing.module.billing.service.CustomerPaymentService;
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
 * G10d customer payments. Every command needs {@code Idempotency-Key} and answers with a {@link CommercialCommandResult};
 * the client re-reads the detail (FE Q25). A record of money received only — no bank or gateway behind it.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Customer payments", description = "Money received from a customer, allocated to posted invoices (G10d)")
public class CustomerPaymentController {

    private static final String KEY = "Idempotency-Key";

    private final CustomerPaymentService paymentService;
    private final CustomerPaymentQueryService queryService;
    private final PlantContextResolver plantContextResolver;

    @GetMapping("/v1/customer-payments")
    @Operation(summary = "List customer payments of a plant")
    public ResponseEntity<ApiResponse<PageResult<CustomerPaymentResponse>>> list(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam(required = false) CustomerPaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(queryService.list(plantId, status, method, customerId, invoiceId,
                fromDate, toDate, search, PageableFactory.of(page, size, "documentDate", "desc", "paymentId"))));
    }

    @GetMapping("/v1/customer-payments/invoice-candidates")
    @Operation(summary = "A customer's posted invoices of a plant with what can still be allocated")
    public ResponseEntity<ApiResponse<PageResult<PaymentInvoiceCandidateResponse>>> invoiceCandidates(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID plantId,
            @RequestParam UUID customerId,
            @RequestParam(required = false) UUID editingPaymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(queryService.invoiceCandidates(plantId, customerId, editingPaymentId,
                PageableFactory.unsorted(page, size))));
    }

    @GetMapping("/v1/customer-payments/{paymentId}")
    @Operation(summary = "Get a customer payment with its allocations")
    public ResponseEntity<ApiResponse<CustomerPaymentResponse>> get(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.get(paymentId)));
    }

    @GetMapping("/v1/customer-payments/{paymentId}/history")
    @Operation(summary = "History of a customer payment")
    public ResponseEntity<ApiResponse<PageResult<CustomerPaymentEventResponse>>> history(
            @PathVariable UUID paymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.history(paymentId, PageableFactory.unsorted(page, size))));
    }

    @PostMapping("/v1/customer-payments")
    @Operation(summary = "Create a DRAFT payment (claims outstanding amounts, pays nothing yet)")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> create(
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CustomerPaymentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(paymentService.create(request, idempotencyKey)));
    }

    @PatchMapping("/v1/customer-payments/{paymentId}")
    @Operation(summary = "Replace a DRAFT payment's date, method, reference, note, amount and allocations")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> update(
            @PathVariable UUID paymentId,
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CustomerPaymentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.update(paymentId, request, idempotencyKey)));
    }

    @PostMapping("/v1/customer-payments/{paymentId}/post")
    @Operation(summary = "Post a payment: the invoices it is allocated to are paid by that much")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> post(
            @PathVariable UUID paymentId,
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CustomerPaymentPostRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.post(paymentId, request, idempotencyKey)));
    }

    @PostMapping("/v1/customer-payments/{paymentId}/cancel")
    @Operation(summary = "Cancel a DRAFT payment (reason required)")
    public ResponseEntity<ApiResponse<CommercialCommandResult>> cancel(
            @PathVariable UUID paymentId,
            @RequestHeader(value = KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CustomerPaymentCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.cancel(paymentId, request, idempotencyKey)));
    }
}
