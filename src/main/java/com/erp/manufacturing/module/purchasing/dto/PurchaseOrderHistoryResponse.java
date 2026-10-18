package com.erp.manufacturing.module.purchasing.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One lifecycle step of a purchase order (G7a): created, confirmed, cancelled, closed. Status moves
 * caused by goods receipts are not listed — the receipts themselves are the record of those. Orders
 * confirmed before G7a have no confirm step (nobody recorded when).
 */
public record PurchaseOrderHistoryResponse(
        String action,
        String fromStatus,
        String toStatus,
        UUID actorId,
        String actorUsername,
        Instant at,
        String reason
) {}
