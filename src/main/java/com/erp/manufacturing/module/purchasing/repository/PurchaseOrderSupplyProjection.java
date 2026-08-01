package com.erp.manufacturing.module.purchasing.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface PurchaseOrderSupplyProjection {
    UUID getItemId();
    BigDecimal getOpenSupplyQuantity();
}
