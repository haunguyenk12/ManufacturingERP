package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItemWarehouseSettingResponse(
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
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
