package com.erp.manufacturing.module.inventory.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * On-hand / reserved / quality-hold / available broken down per {@code (item, warehouse)} — the shape
 * the inventory dashboard needs so an alert line can explain its own availability figure instead of
 * only stating it.
 *
 * <p>Replaces the availability-only projection this query used to return: every consumer that wanted
 * availability per warehouse also wanted to show how that number was reached.
 */
public interface StockQuantityByWarehouseProjection {

    UUID getItemId();

    UUID getWarehouseId();

    BigDecimal getOnHandQuantity();

    BigDecimal getReservedQuantity();

    BigDecimal getQualityHoldQuantity();

    BigDecimal getAvailableQuantity();
}
