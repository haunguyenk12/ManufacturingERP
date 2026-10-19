package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * G7c: one immutable purchase QC decision. No price fields — a QC-only user reads this (FE Q15).
 * {@code childLots} is empty for a non-tracked line: its accepted part went back to the free pool and
 * its rejected part stays on hold, owned by the receipt line ({@code qualityBucketId}).
 */
public record PurchaseQcDecisionResponse(
        UUID decisionId,
        UUID goodsReceiptId,
        String goodsReceiptCode,
        UUID goodsReceiptLineId,
        UUID itemId,
        String itemCode,
        String itemName,
        String uomCode,
        String trackingPolicy,
        BigDecimal acceptedQuantity,
        BigDecimal rejectedQuantity,
        String reason,
        UUID decidedBy,
        String decidedByUsername,
        Instant decidedAt,
        UUID qualityBucketId,
        List<PurchaseQcChildLotResponse> childLots,
        List<UUID> movementIds
) {}
