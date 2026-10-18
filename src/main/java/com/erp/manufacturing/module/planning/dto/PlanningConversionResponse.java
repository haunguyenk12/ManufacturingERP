package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One ledger row: {@code quantity} of a requirement became the draft document {@code targetId}. */
public record PlanningConversionResponse(
        UUID conversionId,
        UUID conversionBatchId,
        UUID mrpRunId,
        UUID requirementLineId,
        UUID supplySuggestionId,
        Integer entryNo,
        BigDecimal quantity,
        String targetType,
        UUID targetId,
        UUID targetLineId,
        String targetCode,
        Integer conversionVersion,
        Instant createdAt,
        UUID createdBy,
        /** G7a: set once the purchase order this row created was cancelled — its quantity counts no more. */
        Instant releasedAt,
        String releaseReason
) {}
