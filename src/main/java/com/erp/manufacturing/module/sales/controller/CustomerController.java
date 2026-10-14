package com.erp.manufacturing.module.sales.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.dto.CustomerCreateRequest;
import com.erp.manufacturing.module.sales.dto.CustomerResponse;
import com.erp.manufacturing.module.sales.dto.CustomerUpdateRequest;
import com.erp.manufacturing.module.sales.service.CustomerService;
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
@Tag(name = "Customers", description = "Customer master data")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping("/v1/companies/{companyId}/customers")
    @Operation(summary = "Create customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody CustomerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(customerService.create(companyId, request)));
    }

    @GetMapping("/v1/companies/{companyId}/customers")
    @Operation(summary = "List customers of a company")
    public ResponseEntity<ApiResponse<PageResult<CustomerResponse>>> list(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.list(
                companyId, status, search, PageableFactory.of(page, size, sortBy, sortDir, "customerId"))));
    }

    @GetMapping("/v1/customers/{customerId}")
    @Operation(summary = "Get customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> get(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.get(customerId)));
    }

    @PatchMapping("/v1/customers/{customerId}")
    @Operation(summary = "Update customer (null keeps a field, blank clears an optional field)")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable UUID customerId,
            @Valid @RequestBody CustomerUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.update(customerId, request)));
    }

    @PostMapping("/v1/customers/{customerId}/activate")
    @Operation(summary = "Activate customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> activate(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.activate(customerId)));
    }

    @PostMapping("/v1/customers/{customerId}/deactivate")
    @Operation(summary = "Deactivate customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> deactivate(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.deactivate(customerId)));
    }
}
