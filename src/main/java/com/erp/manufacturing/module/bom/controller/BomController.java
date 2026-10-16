package com.erp.manufacturing.module.bom.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.dto.*;
import com.erp.manufacturing.module.bom.service.BomService;
import com.erp.manufacturing.common.web.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "BOM", description = "Bill of materials management")
public class BomController {

    private final BomService bomService;

    @PostMapping("/v1/companies/{companyId}/boms")
    @Operation(summary = "Create BOM under company")
    public ResponseEntity<ApiResponse<BomResponse>> createBom(
            @PathVariable UUID companyId,
            @Valid @RequestBody BomCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(bomService.createBom(companyId, request)));
    }

    @GetMapping("/v1/companies/{companyId}/boms")
    @Operation(summary = "List BOMs by company")
    public ResponseEntity<ApiResponse<PageResult<BomResponse>>> listBoms(
            @PathVariable UUID companyId,
            @RequestParam(required = false) UUID parentItemId,
            @RequestParam(required = false) BomStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean latestOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(bomService.listBoms(
                companyId, parentItemId, status, search, latestOnly,
                PageableFactory.of(page, size, sortBy, sortDir, "bomId"))));
    }

    @GetMapping("/v1/boms/{bomId}")
    @Operation(summary = "Get BOM")
    public ResponseEntity<ApiResponse<BomResponse>> getBom(@PathVariable UUID bomId) {
        return ResponseEntity.ok(ApiResponse.ok(bomService.getBom(bomId)));
    }

    @PatchMapping("/v1/boms/{bomId}")
    @Operation(summary = "Update BOM")
    public ResponseEntity<ApiResponse<BomResponse>> updateBom(
            @PathVariable UUID bomId,
            @Valid @RequestBody BomUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bomService.updateBom(bomId, request)));
    }

    @DeleteMapping("/v1/boms/{bomId}")
    @Operation(summary = "Deactivate BOM (soft) — this IS the deactivate command",
            description = "There is no POST /boms/{bomId}/deactivate. Rule C6 forbids hard-deleting "
                    + "business documents, so DELETE moves the revision to INACTIVE and nothing is "
                    + "removed. Work orders already created keep their own BOM snapshot (invariant "
                    + "B12), so deactivating here never changes a work order's requirements.")
    public ResponseEntity<ApiResponse<Void>> deactivateBom(@PathVariable UUID bomId) {
        bomService.deactivateBom(bomId);
        return ResponseEntity.ok(ApiResponse.noContent("BOM deactivated successfully"));
    }

    @PostMapping("/v1/boms/{bomId}/lines")
    @Operation(summary = "Add BOM line")
    public ResponseEntity<ApiResponse<BomResponse>> addLine(
            @PathVariable UUID bomId,
            @Valid @RequestBody BomLineCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(bomService.addLine(bomId, request)));
    }

    @PatchMapping("/v1/bom-lines/{lineId}")
    @Operation(summary = "Update BOM line")
    public ResponseEntity<ApiResponse<BomResponse>> updateLine(
            @PathVariable UUID lineId,
            @Valid @RequestBody BomLineUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bomService.updateLine(lineId, request)));
    }

    @DeleteMapping("/v1/bom-lines/{lineId}")
    @Operation(summary = "Delete BOM line")
    public ResponseEntity<ApiResponse<Void>> deleteLine(@PathVariable UUID lineId) {
        bomService.deleteLine(lineId);
        return ResponseEntity.ok(ApiResponse.noContent("BOM line deleted successfully"));
    }

    @PostMapping("/v1/boms/{bomId}/activate")
    @Operation(summary = "Activate BOM",
            description = "Body {expectedVersion} is optional; when sent, a stale value is 409 "
                    + "CONCURRENT_MODIFICATION. A version whose effectiveFrom is in the future is 409 "
                    + "EFFECTIVE_DATE_FUTURE. The previous ACTIVE version of the product becomes INACTIVE "
                    + "in the same transaction.")
    public ResponseEntity<ApiResponse<BomResponse>> activateBom(
            @PathVariable UUID bomId,
            @RequestBody(required = false) BomVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bomService.activateBom(bomId,
                request == null ? null : request.expectedVersion())));
    }

    @PostMapping("/v1/boms/{bomId}/deactivate")
    @Operation(summary = "Deactivate BOM with an expectedVersion check (same transition as DELETE)")
    public ResponseEntity<ApiResponse<BomResponse>> deactivateBomVersioned(
            @PathVariable UUID bomId,
            @Valid @RequestBody BomExpectedVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bomService.deactivateBom(bomId, request.expectedVersion())));
    }

    @PutMapping("/v1/companies/{companyId}/bom-drafts")
    @Operation(summary = "Save a BOM draft atomically — header and every line in one transaction",
            description = "Without bomId: a new DRAFT version of parentItemId. With bomId + expectedVersion: "
                    + "full replacement of that DRAFT. Lines carry componentQuantity for outputQuantity; the "
                    + "server stores quantityPer = componentQuantity / outputQuantity.")
    public ResponseEntity<ApiResponse<BomResponse>> saveDraft(
            @PathVariable UUID companyId,
            @Valid @RequestBody BomDraftSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bomService.saveDraft(companyId, request)));
    }

    @PostMapping("/v1/boms/{bomId}/copy")
    @Operation(summary = "Copy any BOM version into a new DRAFT of the same product")
    public ResponseEntity<ApiResponse<BomResponse>> copyBom(
            @PathVariable UUID bomId,
            @Valid @RequestBody BomCopyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(bomService.copyBom(bomId, request)));
    }

    @GetMapping("/v1/boms/{bomId}/versions")
    @Operation(summary = "Every version of this BOM's product, newest version first")
    public ResponseEntity<ApiResponse<PageResult<BomResponse>>> listVersions(
            @PathVariable UUID bomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableFactory.of(page, size, "versionNumber", "desc");
        return ResponseEntity.ok(ApiResponse.ok(bomService.listVersions(
                bomId, pageable.getPageNumber(), pageable.getPageSize())));
    }

    @GetMapping("/v1/items/{itemId}/active-bom")
    @Operation(summary = "Get active BOM by item")
    public ResponseEntity<ApiResponse<BomResponse>> getActiveBomByItem(@PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok(bomService.getActiveBomByItem(itemId)));
    }

    @GetMapping("/v1/boms/{bomId}/tree")
    @Operation(summary = "Get BOM tree")
    public ResponseEntity<ApiResponse<BomTreeNodeResponse>> getBomTree(@PathVariable UUID bomId) {
        return ResponseEntity.ok(ApiResponse.ok(bomService.getBomTree(bomId)));
    }

}
