package com.erp.manufacturing.module.dataimport.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.dataimport.domain.ImportRowStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.ImportRowResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportRunResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportValidateRequest;
import com.erp.manufacturing.module.dataimport.service.ImportRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Data Import Runs", description = "Upload, validate and apply spreadsheet imports")
public class ImportRunController {

    private final ImportRunService service;

    @PostMapping(value = "/v1/import-runs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and stage an .xlsx spreadsheet without writing master data")
    public ResponseEntity<ApiResponse<ImportRunResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam ImportTargetType targetType,
            @RequestParam(required = false) UUID profileId,
            @RequestParam UUID companyId,
            @RequestParam(required = false) UUID plantId,
            @RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                service.upload(file, targetType, profileId, companyId, plantId, warehouseId)));
    }

    @PostMapping("/v1/import-runs/{runId}/validate")
    @Operation(summary = "Map, transform and validate staged rows")
    public ResponseEntity<ApiResponse<ImportRunResponse>> validate(
            @PathVariable UUID runId,
            @Valid @RequestBody ImportValidateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.validate(runId, request.profileId())));
    }

    @PostMapping("/v1/import-runs/{runId}/apply")
    @Operation(summary = "Create master data for every valid row")
    public ResponseEntity<ApiResponse<ImportRunResponse>> apply(
            @PathVariable UUID runId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.ok(service.apply(runId, idempotencyKey)));
    }

    @PostMapping("/v1/import-runs/{runId}/cancel")
    @Operation(summary = "Cancel a run that has not been applied")
    public ResponseEntity<ApiResponse<ImportRunResponse>> cancel(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.ok(service.cancel(runId)));
    }

    @GetMapping("/v1/import-runs")
    @Operation(summary = "List import runs for a company")
    public ResponseEntity<ApiResponse<PageResult<ImportRunResponse>>> list(
            @RequestParam UUID companyId,
            @RequestParam(required = false) ImportTargetType targetType,
            @RequestParam(required = false) ImportRunStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(companyId, targetType, status,
                PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/import-runs/{runId}")
    @Operation(summary = "Get an import run and its counters")
    public ResponseEntity<ApiResponse<ImportRunResponse>> get(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(runId)));
    }

    @GetMapping("/v1/import-runs/{runId}/rows")
    @Operation(summary = "List staged rows, optionally filtered by status")
    public ResponseEntity<ApiResponse<PageResult<ImportRowResponse>>> rows(
            @PathVariable UUID runId,
            @RequestParam(required = false) ImportRowStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "rowNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(service.rows(runId, status,
                PageableFactory.of(page, size, sortBy, sortDir))));
    }
}
