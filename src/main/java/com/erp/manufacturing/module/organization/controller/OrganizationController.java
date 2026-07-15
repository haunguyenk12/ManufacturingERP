package com.erp.manufacturing.module.organization.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.dto.*;
import com.erp.manufacturing.module.organization.service.OrganizationService;
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
@Tag(name = "Organization", description = "Company, plant and warehouse management")
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/api/v1/companies")
    @Operation(summary = "List companies")
    public ResponseEntity<ApiResponse<PageResult<CompanyResponse>>> listCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.listCompanies(PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/api/v1/companies")
    @Operation(summary = "Create company")
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @Valid @RequestBody CompanyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(organizationService.createCompany(request)));
    }

    @GetMapping("/api/v1/companies/{companyId}")
    @Operation(summary = "Get company")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable UUID companyId) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.getCompany(companyId)));
    }

    @PatchMapping("/api/v1/companies/{companyId}")
    @Operation(summary = "Update company")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody CompanyUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.updateCompany(companyId, request)));
    }

    @DeleteMapping("/api/v1/companies/{companyId}")
    @Operation(summary = "Deactivate company")
    public ResponseEntity<ApiResponse<Void>> deactivateCompany(@PathVariable UUID companyId) {
        organizationService.deactivateCompany(companyId);
        return ResponseEntity.ok(ApiResponse.noContent("Company deactivated successfully"));
    }

    @GetMapping("/api/v1/companies/{companyId}/plants")
    @Operation(summary = "List plants by company")
    public ResponseEntity<ApiResponse<PageResult<PlantResponse>>> listPlants(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.listPlants(
                companyId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/api/v1/companies/{companyId}/plants")
    @Operation(summary = "Create plant under company")
    public ResponseEntity<ApiResponse<PlantResponse>> createPlant(
            @PathVariable UUID companyId,
            @Valid @RequestBody PlantCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(organizationService.createPlant(companyId, request)));
    }

    @GetMapping("/api/v1/plants/{plantId}")
    @Operation(summary = "Get plant")
    public ResponseEntity<ApiResponse<PlantResponse>> getPlant(@PathVariable UUID plantId) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.getPlant(plantId)));
    }

    @PatchMapping("/api/v1/plants/{plantId}")
    @Operation(summary = "Update plant")
    public ResponseEntity<ApiResponse<PlantResponse>> updatePlant(
            @PathVariable UUID plantId,
            @Valid @RequestBody PlantUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.updatePlant(plantId, request)));
    }

    @DeleteMapping("/api/v1/plants/{plantId}")
    @Operation(summary = "Deactivate plant")
    public ResponseEntity<ApiResponse<Void>> deactivatePlant(@PathVariable UUID plantId) {
        organizationService.deactivatePlant(plantId);
        return ResponseEntity.ok(ApiResponse.noContent("Plant deactivated successfully"));
    }

    @GetMapping("/api/v1/plants/{plantId}/warehouses")
    @Operation(summary = "List warehouses by plant")
    public ResponseEntity<ApiResponse<PageResult<WarehouseResponse>>> listWarehouses(
            @PathVariable UUID plantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.listWarehouses(
                plantId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/api/v1/plants/{plantId}/warehouses")
    @Operation(summary = "Create warehouse under plant")
    public ResponseEntity<ApiResponse<WarehouseResponse>> createWarehouse(
            @PathVariable UUID plantId,
            @Valid @RequestBody WarehouseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(organizationService.createWarehouse(plantId, request)));
    }

    @GetMapping("/api/v1/warehouses/{warehouseId}")
    @Operation(summary = "Get warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getWarehouse(@PathVariable UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.getWarehouse(warehouseId)));
    }

    @PatchMapping("/api/v1/warehouses/{warehouseId}")
    @Operation(summary = "Update warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateWarehouse(
            @PathVariable UUID warehouseId,
            @Valid @RequestBody WarehouseUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.updateWarehouse(warehouseId, request)));
    }

    @DeleteMapping("/api/v1/warehouses/{warehouseId}")
    @Operation(summary = "Deactivate warehouse")
    public ResponseEntity<ApiResponse<Void>> deactivateWarehouse(@PathVariable UUID warehouseId) {
        organizationService.deactivateWarehouse(warehouseId);
        return ResponseEntity.ok(ApiResponse.noContent("Warehouse deactivated successfully"));
    }

}
