package com.erp.manufacturing.module.inventory.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Aggregate inventory read model: exactly one row per item and warehouse. */
public interface StockBalanceAggregateProjection {
    UUID getItemId();
    String getItemCode();
    String getItemName();
    String getUom();
    UUID getWarehouseId();
    BigDecimal getOnHandQuantity();
    BigDecimal getReservedQuantity();
    BigDecimal getAvailableQuantity();
    BigDecimal getQualityHoldQuantity();
    BigDecimal getRejectedQuantity();
    BigDecimal getExpiredQuantity();
    Long getLotCount();
    Instant getUpdatedAt();
}
