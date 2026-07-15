package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
        Instant createdAt
) {}
