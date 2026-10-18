package com.erp.manufacturing.module.planning.dto;

import com.erp.manufacturing.module.planning.domain.MrpRunInputSnapshot;

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
        Instant updatedAt,
        /** {@code STANDARD} or {@code COMMERCIAL} (G6b). */
        String runKind,
        /** Opaque token of the run's sources at calculation time; {@code null} on standard runs. */
        String sourceVersion,
        /**
         * {@code CURRENT}/{@code STALE} against today's sources — computed only by
         * {@code GET /planning-runs/{id}} on a commercial run; {@code null} elsewhere.
         */
        String currentSourceState,
        Integer conversionVersion,
        MrpRunInputSnapshot inputSnapshot
) {
    public MrpRunResponse(UUID mrpRunId, String code, UUID companyId, String companyCode, UUID plantId,
                          String plantCode, UUID warehouseId, String warehouseCode, LocalDate horizonStartDate,
                          LocalDate horizonEndDate, String status, Instant startedAt, Instant completedAt,
                          Integer totalDemandLines, Integer totalRequirementLines, Integer totalSuggestionLines,
                          BigDecimal grossDemandQuantity, Integer shortageLines, Integer plannedWorkOrders,
                          Integer plannedPurchaseRecommendations, Integer blockedProposals, String errorMessage,
                          Instant createdAt, Instant updatedAt) {
        this(mrpRunId, code, companyId, companyCode, plantId, plantCode, warehouseId, warehouseCode,
                horizonStartDate, horizonEndDate, status, startedAt, completedAt, totalDemandLines,
                totalRequirementLines, totalSuggestionLines, grossDemandQuantity, shortageLines,
                plannedWorkOrders, plannedPurchaseRecommendations, blockedProposals, errorMessage,
                createdAt, updatedAt, "STANDARD", null, null, 0, null);
    }
}
