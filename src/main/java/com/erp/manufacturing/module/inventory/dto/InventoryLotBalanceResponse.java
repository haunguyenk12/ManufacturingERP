package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One warehouse row in {@link InventoryLotDetailResponse#balances()} (C2-2). */
public record InventoryLotBalanceResponse(
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        BigDecimal onHandQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity
) {}
