package com.erp.manufacturing.module.inventory.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface StockAvailabilityProjection {

    UUID getItemId();

    BigDecimal getQuantity();
}
