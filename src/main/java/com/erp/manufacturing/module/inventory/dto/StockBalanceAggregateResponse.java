package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockBalanceAggregateResponse(
        UUID itemId,
        String itemCode,
        String itemName,
        String uom,
        UUID warehouseId,
        BigDecimal onHandQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        BigDecimal qualityHoldQuantity,
        BigDecimal rejectedQuantity,
        BigDecimal expiredQuantity,
        Long lotCount,
        Instant updatedAt
) {}
