package com.erp.manufacturing.module.planning.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MrpRunResponse(
        UUID mrpRunId,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        UUID warehouseId,
        String warehouseCode,
        LocalDate horizonStartDate,
        LocalDate horizonEndDate,
        String status,
        Instant startedAt,
        Instant completedAt,
        Integer totalDemandLines,
        Integer totalRequirementLines,
        Integer totalSuggestionLines,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {}
