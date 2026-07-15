package com.erp.manufacturing.module.inventory.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface ItemWarehousePlanningSettingProjection {
    UUID getItemId();
    BigDecimal getSafetyStockQuantity();
    BigDecimal getReorderPointQuantity();
    Integer getLeadTimeDays();
}
