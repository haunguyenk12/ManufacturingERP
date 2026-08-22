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
        boolean defaultSupply,
        boolean defaultOutput,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public ItemWarehouseSettingResponse(UUID settingId, UUID itemId, String itemCode, String itemName,
                                        UUID warehouseId, String warehouseCode, String warehouseName,
                                        BigDecimal safetyStock, BigDecimal reorderPoint, Integer leadTimeDays,
                                        String status, Instant createdAt, Instant updatedAt) {
        this(settingId, itemId, itemCode, itemName, warehouseId, warehouseCode, warehouseName,
                safetyStock, reorderPoint, leadTimeDays, false, false, status, createdAt, updatedAt);
    }
}
