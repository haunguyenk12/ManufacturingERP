package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * G7a (D12): a planning conversion behind a purchase order line, from the G6b ledger.
 * {@code releasedAt} set ⇒ the order was cancelled and the quantity went back to the run.
 */
public record PurchaseOrderLineSourceResponse(
        UUID conversionId,
        UUID runId,
        String runCode,
        UUID requirementId,
        UUID suggestionId,
        String sourceVersion,
        BigDecimal convertedQuantity,
        Instant convertedAt,
        Instant releasedAt,
        String releaseReason
) {}
