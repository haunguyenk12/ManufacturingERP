package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryAlertLineResponse(
        UUID settingId,
        UUID itemId,
        String itemCode,
        String itemName,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        BigDecimal safetyStock,
        BigDecimal reorderPoint,
        Integer leadTimeDays,
        BigDecimal availableQuantity,
        String status
) {}
