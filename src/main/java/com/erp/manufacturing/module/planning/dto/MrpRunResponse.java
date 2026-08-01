package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MrpRunResponse(
        UUID mrpRunId,
        String code,
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
        /**
         * Total gross demand this run started from (spec §2.4 Run header) — the sum of
         * {@code grossRequiredQuantity} over the level-0 requirements. Counted in place from the
         * calculation result, like the four counters V36 added; zero on runs that predate F8.
         */
        BigDecimal grossDemandQuantity,
        Integer shortageLines,
        Integer plannedWorkOrders,
        Integer plannedPurchaseRecommendations,
        Integer blockedProposals,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {}
