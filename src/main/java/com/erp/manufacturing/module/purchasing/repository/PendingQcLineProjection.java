package com.erp.manufacturing.module.purchasing.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** CF-4: one goods receipt line still holding pending purchase-QC quantity — no price, no supplier contact. */
public interface PendingQcLineProjection {

    UUID getGoodsReceiptId();

    UUID getGoodsReceiptLineId();

    String getReceiptNo();

    Long getReceiptVersion();

    UUID getItemId();

    UUID getWarehouseId();

    String getUomCode();

    BigDecimal getPendingQuantity();
}
