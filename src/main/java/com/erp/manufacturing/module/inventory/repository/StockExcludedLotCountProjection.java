package com.erp.manufacturing.module.inventory.repository;

import java.util.UUID;

public interface StockExcludedLotCountProjection {
    UUID getItemId();
    Long getExcludedLotCount();
}
