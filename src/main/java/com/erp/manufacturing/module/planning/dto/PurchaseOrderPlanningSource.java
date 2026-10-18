package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * G7a (D12): one planning conversion behind a purchase order line, read from the G6b ledger — the
 * purchase order keeps no source columns of its own. {@code releasedAt} is set once the order was
 * cancelled and the quantity went back to the run.
 */
public record PurchaseOrderPlanningSource(
        UUID purchaseOrderLineId,
        UUID conversionId,
        UUID mrpRunId,
        String mrpRunCode,
        UUID requirementLineId,
        UUID supplySuggestionId,
        String sourceVersion,
        BigDecimal convertedQuantity,
        Instant convertedAt,
        Instant releasedAt,
        String releaseReason
) {}
