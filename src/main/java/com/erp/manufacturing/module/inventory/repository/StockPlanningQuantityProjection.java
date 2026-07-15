package com.erp.manufacturing.module.inventory.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface StockPlanningQuantityProjection {
    UUID getItemId();
    BigDecimal getOnHandQuantity();
    BigDecimal getReservedQuantity();
    BigDecimal getAvailableQuantity();
}
