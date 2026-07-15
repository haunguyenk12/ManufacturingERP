package com.erp.manufacturing.module.workorder.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface WorkOrderSupplyProjection {
    UUID getItemId();
    BigDecimal getOpenSupplyQuantity();
}
