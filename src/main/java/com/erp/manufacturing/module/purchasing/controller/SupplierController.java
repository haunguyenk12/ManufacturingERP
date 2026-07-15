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

    @PostMapping("/api/v1/suppliers")
    @Operation(summary = "Create supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> create(@Valid @RequestBody SupplierCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(supplierService.create(request)));
    }

    @GetMapping("/api/v1/suppliers")
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

    @GetMapping("/api/v1/suppliers/{supplierId}")
    @Operation(summary = "Get supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> get(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.get(supplierId)));
    }

    @PatchMapping("/api/v1/suppliers/{supplierId}")
    @Operation(summary = "Update supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable UUID supplierId,
            @Valid @RequestBody SupplierUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.update(supplierId, request)));
    }

    @PatchMapping("/api/v1/suppliers/{supplierId}/deactivate")
    @Operation(summary = "Deactivate supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> deactivate(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.deactivate(supplierId)));
    }

    @PostMapping("/api/v1/items/{itemId}/suppliers")
    @Operation(summary = "Add supplier for item")
    public ResponseEntity<ApiResponse<ItemSupplierResponse>> addItemSupplier(
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(supplierService.addItemSupplier(itemId, request)));
    }

    @GetMapping("/api/v1/items/{itemId}/suppliers")
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

    @PatchMapping("/api/v1/items/{itemId}/suppliers/{itemSupplierId}")
    @Operation(summary = "Update item supplier")
    public ResponseEntity<ApiResponse<ItemSupplierResponse>> updateItemSupplier(
            @PathVariable UUID itemId,
            @PathVariable UUID itemSupplierId,
            @Valid @RequestBody ItemSupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                supplierService.updateItemSupplier(itemId, itemSupplierId, request)));
    }

    @PatchMapping("/api/v1/items/{itemId}/suppliers/{itemSupplierId}/deactivate")
    @Operation(summary = "Deactivate item supplier")
    public ResponseEntity<ApiResponse<ItemSupplierResponse>> deactivateItemSupplier(
            @PathVariable UUID itemId,
            @PathVariable UUID itemSupplierId) {
        return ResponseEntity.ok(ApiResponse.ok(
                supplierService.deactivateItemSupplier(itemId, itemSupplierId)));
    }

}
