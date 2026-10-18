package com.erp.manufacturing.module.dataimport.controller;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchCommitRequest;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchResponse;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchSummaryResponse;
import com.erp.manufacturing.module.dataimport.service.DataImportBatchService;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fixed-template spreadsheet import (G3, FE_HANDOFF_GD_03 §3.4). The mapped-profile path stays on
 * {@code /v1/import-runs} ({@link ImportRunController}) with its own semantics.
 *
 * <p>{@code X-Plant-Id} is cross-checked only where the request names a plant itself — upload and list
 * of items. Detail and commit are identified by the batch, whose plant the server already knows
 * (error-handling.md §5.6.1).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Data Import Batches", description = "Validate and commit omniplant-import-v1 workbooks")
public class DataImportBatchController {

    private static final Set<ImportRunStatus> BATCH_STATUSES =
            Set.of(ImportRunStatus.READY, ImportRunStatus.INVALID, ImportRunStatus.COMMITTED);

    private final DataImportBatchService service;
    private final PlantContextResolver plantContextResolver;

    @PostMapping(value = "/v1/data-import/batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and validate a workbook; creates no master data. Row issues come back "
            + "as an INVALID batch with HTTP 201")
    public ResponseEntity<ApiResponse<DataImportBatchResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam String kind,
            @RequestParam UUID companyId,
            @RequestParam(required = false) UUID plantId,
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        DataImportKind importKind = parseKind(kind);
        UUID plant = plantFor(importKind, plantId, plantHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                service.upload(file, importKind, companyId, plant, idempotencyKey)));
    }

    @PostMapping("/v1/data-import/batches/{batchId}/commit")
    @Operation(summary = "Create every row of a READY batch in one transaction, or none of them")
    public ResponseEntity<ApiResponse<DataImportBatchResponse>> commit(
            @PathVariable UUID batchId,
            @Valid @RequestBody DataImportBatchCommitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.commit(batchId, request.expectedVersion(), idempotencyKey)));
    }

    @GetMapping("/v1/data-import/batches")
    @Operation(summary = "List the caller's own batches, newest first")
    public ResponseEntity<ApiResponse<PageResult<DataImportBatchSummaryResponse>>> list(
            @RequestParam UUID companyId,
            @RequestParam(required = false) UUID plantId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader) {
        DataImportKind importKind = kind == null || kind.isBlank() ? null : parseKind(kind);
        if (importKind != null && importKind.plantScoped() && plantId == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("plantId", "is required when kind is " + importKind.wireValue()));
        }
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(service.list(companyId, plantId, importKind,
                parseStatus(status), search,
                PageableFactory.of(page, size, "createdAt", "desc", "importRunId"))));
    }

    @GetMapping("/v1/data-import/batches/{batchId}")
    @Operation(summary = "Get one of the caller's batches with every row, issue and created id")
    public ResponseEntity<ApiResponse<DataImportBatchResponse>> get(@PathVariable UUID batchId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(batchId)));
    }

    /** Items and sales orders need a plant; partner/BOM batches ignore one, so a plant switcher in the UI is harmless. */
    private UUID plantFor(DataImportKind kind, UUID plantId, String plantHeader) {
        if (!kind.plantScoped()) {
            return null;
        }
        if (plantId == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("plantId", "is required when kind is " + kind.wireValue()));
        }
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return plantId;
    }

    private DataImportKind parseKind(String kind) {
        return DataImportKind.fromWire(kind).orElseThrow(() -> ExceptionFactory.withErrors(
                ValidationErrorCode.INVALID_INPUT,
                Map.of("kind", "must be one of " + DataImportKind.wireValues())));
    }

    private ImportRunStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            ImportRunStatus parsed = ImportRunStatus.valueOf(status.trim());
            if (BATCH_STATUSES.contains(parsed)) {
                return parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // falls through to the same field error as a status of the other path
        }
        throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                Map.of("status", "must be one of READY, INVALID, COMMITTED"));
    }
}
