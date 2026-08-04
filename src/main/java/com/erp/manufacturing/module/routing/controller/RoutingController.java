package com.erp.manufacturing.module.routing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.dto.RoutingCreateRequest;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import com.erp.manufacturing.module.routing.service.RoutingService;
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
@Tag(name = "Routing", description = "Production routing master data")
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping("/api/v1/companies/{companyId}/routings")
    @Operation(summary = "Create routing (DRAFT) with its operations")
    public ResponseEntity<ApiResponse<RoutingResponse>> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody RoutingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(routingService.create(companyId, request)));
    }

    @GetMapping("/api/v1/companies/{companyId}/routings")
    @Operation(summary = "List routings by company")
    public ResponseEntity<ApiResponse<PageResult<RoutingResponse>>> list(
            @PathVariable UUID companyId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) RoutingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(routingService.list(
                companyId, itemId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/routings/{routingId}")
    @Operation(summary = "Get routing")
    public ResponseEntity<ApiResponse<RoutingResponse>> get(@PathVariable UUID routingId) {
        return ResponseEntity.ok(ApiResponse.ok(routingService.get(routingId)));
    }

    @PostMapping("/api/v1/routings/{routingId}/activate")
    @Operation(summary = "Activate routing; deactivates the previous ACTIVE routing of the same item")
    public ResponseEntity<ApiResponse<RoutingResponse>> activate(@PathVariable UUID routingId) {
        return ResponseEntity.ok(ApiResponse.ok(routingService.activate(routingId)));
    }

    @DeleteMapping("/api/v1/routings/{routingId}")
    @Operation(summary = "Deactivate routing (soft) — this IS the deactivate command",
            description = "There is no POST /routings/{routingId}/deactivate. Rule C6 forbids "
                    + "hard-deleting business documents, so DELETE moves the routing to INACTIVE and "
                    + "nothing is removed. Routing snapshots already captured onto work orders stay "
                    + "frozen (invariant B49); new work orders created from MRP will be refused with "
                    + "MISSING_ROUTING (409) while no ACTIVE routing exists for the item.")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID routingId) {
        routingService.deactivate(routingId);
        return ResponseEntity.ok(ApiResponse.noContent("Routing deactivated successfully"));
    }
}
