package com.erp.manufacturing.module.routing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.dto.RoutingCopyRequest;
import com.erp.manufacturing.module.routing.dto.RoutingCreateRequest;
import com.erp.manufacturing.module.routing.dto.RoutingDraftSaveRequest;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import com.erp.manufacturing.module.routing.dto.RoutingVersionRequest;
import com.erp.manufacturing.module.routing.service.RoutingService;
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
@Tag(name = "Routing", description = "Production routing master data")
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping("/v1/companies/{companyId}/routings")
    @Operation(summary = "Create routing (DRAFT) with its operations")
    public ResponseEntity<ApiResponse<RoutingResponse>> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody RoutingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(routingService.create(companyId, request)));
    }

    @PutMapping("/v1/companies/{companyId}/routing-drafts")
    @Operation(summary = "Save a routing draft and its whole operation graph atomically",
            description = "Without routingId: a new DRAFT version of itemId (409 OPEN_DRAFT_EXISTS when the "
                    + "product already has one). With routingId + expectedVersion: full replacement of that "
                    + "DRAFT. Operations reference each other by clientKey; a predecessor must have a "
                    + "smaller sequence.")
    public ResponseEntity<ApiResponse<RoutingResponse>> saveDraft(
            @PathVariable UUID companyId,
            @Valid @RequestBody RoutingDraftSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(routingService.saveDraft(companyId, request)));
    }

    @GetMapping("/v1/companies/{companyId}/routings")
    @Operation(summary = "List routings by company",
            description = "plantId keeps routings whose work centers belong to that plant; search matches "
                    + "routing code, product code or product name.")
    public ResponseEntity<ApiResponse<PageResult<RoutingResponse>>> list(
            @PathVariable UUID companyId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) RoutingStatus status,
            @RequestParam(required = false) UUID plantId,
            @RequestParam(required = false) UUID workCenterId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(routingService.list(
                companyId, itemId, status, plantId, workCenterId, search,
                PageableFactory.of(page, size, sortBy, sortDir, "routingId"))));
    }

    @GetMapping("/v1/routings/{routingId}")
    @Operation(summary = "Get routing")
    public ResponseEntity<ApiResponse<RoutingResponse>> get(@PathVariable UUID routingId) {
        return ResponseEntity.ok(ApiResponse.ok(routingService.get(routingId)));
    }

    @GetMapping("/v1/routings/{routingId}/versions")
    @Operation(summary = "Every version of this routing's product, newest version first")
    public ResponseEntity<ApiResponse<PageResult<RoutingResponse>>> listVersions(
            @PathVariable UUID routingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableFactory.of(page, size, "versionNumber", "desc");
        return ResponseEntity.ok(ApiResponse.ok(routingService.listVersions(
                routingId, pageable.getPageNumber(), pageable.getPageSize())));
    }

    @PostMapping("/v1/routings/{routingId}/copy")
    @Operation(summary = "Copy any routing version into a new DRAFT of the same product and code")
    public ResponseEntity<ApiResponse<RoutingResponse>> copy(
            @PathVariable UUID routingId,
            @Valid @RequestBody RoutingCopyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(routingService.copy(routingId, request)));
    }

    @PostMapping("/v1/routings/{routingId}/activate")
    @Operation(summary = "Activate routing; deactivates the previous ACTIVE routing of the same item",
            description = "Body {expectedVersion} is optional. A routing whose effectiveFrom is in the future "
                    + "is 409 EFFECTIVE_DATE_FUTURE.")
    public ResponseEntity<ApiResponse<RoutingResponse>> activate(
            @PathVariable UUID routingId,
            @RequestBody(required = false) RoutingVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(routingService.activate(routingId,
                request == null ? null : request.expectedVersion())));
    }

    @PostMapping("/v1/routings/{routingId}/deactivate")
    @Operation(summary = "Deactivate routing with an expectedVersion check (same transition as DELETE)")
    public ResponseEntity<ApiResponse<RoutingResponse>> deactivateVersioned(
            @PathVariable UUID routingId,
            @Valid @RequestBody RoutingVersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(routingService.deactivate(routingId, request.expectedVersion())));
    }

    @DeleteMapping("/v1/routings/{routingId}")
    @Operation(summary = "Deactivate routing (soft)",
            description = "Rule C6 forbids hard-deleting business documents, so DELETE moves the routing to "
                    + "INACTIVE and nothing is removed. POST /routings/{routingId}/deactivate does the same "
                    + "with an expectedVersion check. Routing snapshots already captured onto work orders stay "
                    + "frozen (invariant B49); new work orders created from MRP will be refused with "
                    + "MISSING_ROUTING (409) while no ACTIVE routing exists for the item.")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID routingId) {
        routingService.deactivate(routingId);
        return ResponseEntity.ok(ApiResponse.noContent("Routing deactivated successfully"));
    }
}
