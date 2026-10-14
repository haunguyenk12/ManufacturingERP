package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.purchasing.domain.SupplierStatus;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.service.SupplierService;
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
@Tag(name = "Suppliers", description = "Supplier master data and item suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping("/v1/suppliers")
    @Operation(summary = "Create supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> create(@Valid @RequestBody SupplierCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(supplierService.create(request)));
    }

    @GetMapping("/v1/suppliers")
    @Operation(summary = "List suppliers")
    public ResponseEntity<ApiResponse<PageResult<SupplierResponse>>> list(
            @RequestParam UUID companyId,
            @RequestParam(required = false) SupplierStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.list(
                companyId, status, keyword, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/suppliers/{supplierId}")
    @Operation(summary = "Get supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> get(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.get(supplierId)));
    }

    @PatchMapping("/v1/suppliers/{supplierId}")
    @Operation(summary = "Update supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable UUID supplierId,
            @Valid @RequestBody SupplierUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.update(supplierId, request)));
    }

    @PatchMapping("/v1/suppliers/{supplierId}/deactivate")
    @Operation(summary = "Deactivate supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> deactivate(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.deactivate(supplierId)));
    }

    // G2-2: the company-scoped shape FE §3.4 uses for both partner catalogues. Added alongside the
    // routes above, not instead of them — purchasing screens and seed scripts still call those.

    @PostMapping("/v1/companies/{companyId}/suppliers")
    @Operation(summary = "Create supplier in a company")
    public ResponseEntity<ApiResponse<SupplierResponse>> createForCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody CompanySupplierCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(supplierService.createForCompany(companyId, request)));
    }

    @GetMapping("/v1/companies/{companyId}/suppliers")
    @Operation(summary = "List suppliers of a company")
    public ResponseEntity<ApiResponse<PageResult<SupplierResponse>>> listForCompany(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SupplierStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.list(
                companyId, status, search, PageableFactory.of(page, size, sortBy, sortDir, "supplierId"))));
    }

    @PostMapping("/v1/suppliers/{supplierId}/activate")
    @Operation(summary = "Activate supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> activate(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.activate(supplierId)));
    }

    @PostMapping("/v1/suppliers/{supplierId}/deactivate")
    @Operation(summary = "Deactivate supplier (POST form of the PATCH route above)")
    public ResponseEntity<ApiResponse<SupplierResponse>> deactivateViaPost(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.deactivate(supplierId)));
    }

    @PostMapping("/v1/items/{itemId}/suppliers")
    @Operation(summary = "Add supplier for item")
    public ResponseEntity<ApiResponse<ItemSupplierResponse>> addItemSupplier(
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(supplierService.addItemSupplier(itemId, request)));
    }

    @GetMapping("/v1/items/{itemId}/suppliers")
    @Operation(summary = "List suppliers for item")
    public ResponseEntity<ApiResponse<PageResult<ItemSupplierResponse>>> listItemSuppliers(
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "preferred") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.listItemSuppliers(
                itemId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PatchMapping("/v1/items/{itemId}/suppliers/{itemSupplierId}")
    @Operation(summary = "Update item supplier")
    public ResponseEntity<ApiResponse<ItemSupplierResponse>> updateItemSupplier(
            @PathVariable UUID itemId,
            @PathVariable UUID itemSupplierId,
            @Valid @RequestBody ItemSupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                supplierService.updateItemSupplier(itemId, itemSupplierId, request)));
    }

    @PatchMapping("/v1/items/{itemId}/suppliers/{itemSupplierId}/deactivate")
    @Operation(summary = "Deactivate item supplier")
    public ResponseEntity<ApiResponse<ItemSupplierResponse>> deactivateItemSupplier(
            @PathVariable UUID itemId,
            @PathVariable UUID itemSupplierId) {
        return ResponseEntity.ok(ApiResponse.ok(
                supplierService.deactivateItemSupplier(itemId, itemSupplierId)));
    }

}
