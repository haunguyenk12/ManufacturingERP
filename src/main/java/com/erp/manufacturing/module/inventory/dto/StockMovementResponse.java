package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code traceId}/{@code createdBy}/{@code createdByUsername} (G4-4, R12) are server-recorded:
 * {@code traceId} is the business trace persisted on the ledger row (not the {@code X-Trace-Id} header,
 * error-handling.md §5.1), {@code createdBy} the authenticated actor. All three are {@code null} on
 * rows written before they were recorded, or by a flow with no request user.
 */
public record StockMovementResponse(
        UUID movementId,
        UUID itemId,
        UUID warehouseId,
        UUID lotId,
        String lotCode,
        String movementType,
        String direction,
        BigDecimal quantity,
        String reason,
        String referenceType,
        String referenceId,
        String idempotencyKey,
        Instant createdAt,
        String traceId,
        UUID createdBy,
        String createdByUsername
) {}
