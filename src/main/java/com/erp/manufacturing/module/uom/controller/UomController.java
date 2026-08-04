package com.erp.manufacturing.module.uom.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.uom.domain.UomStatus;
import com.erp.manufacturing.module.uom.dto.UomCreateRequest;
import com.erp.manufacturing.module.uom.dto.UomResponse;
import com.erp.manufacturing.module.uom.dto.UomUpdateRequest;
import com.erp.manufacturing.module.uom.service.UomService;
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

/**
 * Unit of measure master data (C2-3, {@code BACKEND_CAPSTONE2_API_GAPS.md §3.1}).
 *
 * <p>Global — no {@code X-Plant-Id} cross-check on any endpoint here (error-handling.md §5.6.1): the
 * header only has meaning where there are two sources of plant to compare, and UOM has none.
 *
 * <p>Lifecycle uses {@code POST .../activate} + {@code POST .../deactivate} rather than this repo's
 * more common {@code DELETE} deactivate (rule C6): this is greenfield API and the FE gap doc lists
 * these two verbs explicitly, so following it avoids reproducing the "endpoint exists, FE didn't
 * recognize it" round-trip that C2-0 had to resolve for BOM/Routing.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "UOM", description = "Unit of measure master data")
public class UomController {

    private final UomService uomService;

    @PostMapping("/api/v1/uoms")
    @Operation(summary = "Create a unit of measure")
    public ResponseEntity<ApiResponse<UomResponse>> create(@Valid @RequestBody UomCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(uomService.create(request)));
    }

    @GetMapping("/api/v1/uoms")
    @Operation(summary = "List units of measure")
    public ResponseEntity<ApiResponse<PageResult<UomResponse>>> list(
            @RequestParam(required = false) UomStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(uomService.list(
                status, search, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/uoms/{uomId}")
    @Operation(summary = "Get a unit of measure")
    public ResponseEntity<ApiResponse<UomResponse>> get(@PathVariable UUID uomId) {
        return ResponseEntity.ok(ApiResponse.ok(uomService.get(uomId)));
    }

    @PatchMapping("/api/v1/uoms/{uomId}")
    @Operation(summary = "Update name/description of a unit of measure",
            description = "code is immutable after creation and is not part of this request body.")
    public ResponseEntity<ApiResponse<UomResponse>> update(
            @PathVariable UUID uomId, @Valid @RequestBody UomUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(uomService.update(uomId, request)));
    }

    @PostMapping("/api/v1/uoms/{uomId}/activate")
    @Operation(summary = "Activate a unit of measure (idempotent — no-op if already ACTIVE)")
    public ResponseEntity<ApiResponse<UomResponse>> activate(@PathVariable UUID uomId) {
        return ResponseEntity.ok(ApiResponse.ok(uomService.activate(uomId)));
    }

    @PostMapping("/api/v1/uoms/{uomId}/deactivate")
    @Operation(summary = "Deactivate a unit of measure (idempotent — no-op if already INACTIVE)")
    public ResponseEntity<ApiResponse<UomResponse>> deactivate(@PathVariable UUID uomId) {
        return ResponseEntity.ok(ApiResponse.ok(uomService.deactivate(uomId)));
    }
}
