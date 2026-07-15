package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockBalanceResponse(
        UUID balanceId,
        UUID itemId,
        UUID warehouseId,
        UUID lotId,
        String lotCode,
        BigDecimal quantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        Instant updatedAt
) {}
