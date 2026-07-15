package com.erp.manufacturing.module.planning.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateRequest;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateResponse;
import com.erp.manufacturing.module.planning.service.PlanningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Planning", description = "Production estimation and shortage reports")
public class PlanningController {

    private final PlanningService planningService;

    @PostMapping("/api/v1/planning/production-estimates")
    @Operation(summary = "Estimate production capability and shortages")
    public ResponseEntity<ApiResponse<ProductionEstimateResponse>> estimateProduction(
            @Valid @RequestBody ProductionEstimateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(planningService.estimateProduction(request)));
    }
}
