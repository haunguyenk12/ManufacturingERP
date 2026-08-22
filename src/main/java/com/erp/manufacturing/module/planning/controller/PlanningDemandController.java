package com.erp.manufacturing.module.planning.controller;

import com.erp.manufacturing.common.web.PlantContextResolver;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.planning.domain.PlanningDemandStatus;
import com.erp.manufacturing.module.planning.dto.PlanningDemandCreateRequest;
import com.erp.manufacturing.module.planning.dto.PlanningDemandResponse;
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
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
@Tag(name = "Planning Demands", description = "Demand inputs for MRP planning")
public class PlanningDemandController {

    private final PlanningDemandService planningDemandService;
    private final PlantContextResolver plantContextResolver;

    @PostMapping("/v1/planning/demands")
    @Operation(summary = "Create planning demand")
    public ResponseEntity<ApiResponse<PlanningDemandResponse>> create(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @Valid @RequestBody PlanningDemandCreateRequest request) {
        plantContextResolver.ensureMatches(plantHeader, request.plantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(planningDemandService.create(request)));
    }

    @GetMapping("/v1/planning/demands")
    @Operation(summary = "List planning demands")
    public ResponseEntity<ApiResponse<PageResult<PlanningDemandResponse>>> list(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) PlanningDemandStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dueDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(planningDemandService.list(
                companyId, plantId, warehouseId, itemId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/planning/demands/{demandId}")
    @Operation(summary = "Get planning demand")
    public ResponseEntity<ApiResponse<PlanningDemandResponse>> get(@PathVariable UUID demandId) {
        return ResponseEntity.ok(ApiResponse.ok(planningDemandService.get(demandId)));
    }

    @PatchMapping("/v1/planning/demands/{demandId}/cancel")
    @Operation(summary = "Cancel planning demand")
    public ResponseEntity<ApiResponse<PlanningDemandResponse>> cancel(@PathVariable UUID demandId) {
        return ResponseEntity.ok(ApiResponse.ok(planningDemandService.cancel(demandId)));
    }

}
