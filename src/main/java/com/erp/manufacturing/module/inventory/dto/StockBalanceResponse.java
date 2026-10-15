package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One stock position. {@code version} (G4-3) is what {@code POST /v1/inventory/counts} expects back as
 * {@code expectedVersion}: it moves with every write to this position.
 */
public record StockBalanceResponse(
        UUID balanceId,
        UUID itemId,
        UUID warehouseId,
        UUID lotId,
        String lotCode,
        BigDecimal quantity,
        BigDecimal reservedQuantity,
        BigDecimal qualityHoldQuantity,
        BigDecimal availableQuantity,
        Instant updatedAt,
        Long version
) {}
