package com.erp.manufacturing.module.inventory.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface StockAvailabilityByWarehouseProjection {

    UUID getItemId();

    UUID getWarehouseId();

    BigDecimal getQuantity();
}
