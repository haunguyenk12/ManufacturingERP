package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * G9c: one immutable production QC decision. {@code outputLots} are the lots the decision put its quantities in:
 * the child lots of a partial decision on tracked output, or the receipt's own lot when the whole lot changed
 * status (old {@code qc-disposition} endpoint, or a decision recorded before V85). Empty for non-tracked output,
 * whose quantities stay in the pool owned by {@code qualityBucketId} (the receipt line, D4).
 */
public record ProductionQcDecisionResponse(
        UUID qcDecisionId,
        UUID receiptId,
        String receiptCode,
        UUID receiptLineId,
        BigDecimal acceptedQuantity,
        BigDecimal rejectedQuantity,
        String reason,
        UUID actorId,
        String actorName,
        Instant decidedAt,
        UUID qualityBucketId,
        List<ProductionQcLotResponse> outputLots,
        List<UUID> stockMovementIds
) {}
