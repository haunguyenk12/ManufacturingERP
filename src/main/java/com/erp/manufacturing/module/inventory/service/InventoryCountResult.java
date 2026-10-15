package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.domain.StockMovement;

import java.math.BigDecimal;

/** {@code previousQuantity} is the quantity the count replaced; {@code balance} is the position after it. */
public record InventoryCountResult(StockMovement movement, StockBalance balance, BigDecimal previousQuantity) {}
