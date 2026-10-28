package com.erp.manufacturing.module.purchasing.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** CF-3: purchase-QC quantity of non-lot goods receipt lines still sitting in a quality hold, per item and warehouse. */
public interface PurchaseQcHoldProjection {

    UUID getItemId();

    UUID getWarehouseId();

    BigDecimal getPendingQuantity();

    BigDecimal getRejectedQuantity();
}
