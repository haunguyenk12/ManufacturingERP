package com.erp.manufacturing.module.dataimport.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.dataimport.domain.ImportProfileStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.ImportProfileCreateRequest;
import com.erp.manufacturing.module.dataimport.dto.ImportProfileResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportProfileUpdateRequest;
import com.erp.manufacturing.module.dataimport.service.ImportProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Data Import Profiles", description = "Saved spreadsheet column mappings")
public class ImportProfileController {

    private final ImportProfileService service;

    @PostMapping("/v1/import-profiles")
    @Operation(summary = "Create an import mapping profile")
    public ResponseEntity<ApiResponse<ImportProfileResponse>> create(
            @Valid @RequestBody ImportProfileCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/v1/import-profiles")
    @Operation(summary = "List company and shared import profiles")
    public ResponseEntity<ApiResponse<PageResult<ImportProfileResponse>>> list(
            @RequestParam UUID companyId,
            @RequestParam(required = false) ImportTargetType targetType,
            @RequestParam(required = false) ImportProfileStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(companyId, targetType, status,
                PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/import-profiles/{profileId}")
    @Operation(summary = "Get an import profile")
    public ResponseEntity<ApiResponse<ImportProfileResponse>> get(@PathVariable UUID profileId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(profileId)));
    }

    @PatchMapping("/v1/import-profiles/{profileId}")
    @Operation(summary = "Replace the editable mapping-profile fields")
    public ResponseEntity<ApiResponse<ImportProfileResponse>> update(
            @PathVariable UUID profileId,
            @Valid @RequestBody ImportProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(profileId, request)));
    }
}
