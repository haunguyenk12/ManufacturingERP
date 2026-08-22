package com.erp.manufacturing.module.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemWarehouseSettingRequest(
        @NotNull UUID itemId,
        @NotNull UUID warehouseId,
        @NotNull @PositiveOrZero BigDecimal safetyStock,
        @NotNull @PositiveOrZero BigDecimal reorderPoint,
        @NotNull @Min(0) Integer leadTimeDays,
        boolean defaultSupply,
        boolean defaultOutput
) {
    public ItemWarehouseSettingRequest(UUID itemId, UUID warehouseId, BigDecimal safetyStock,
                                       BigDecimal reorderPoint, Integer leadTimeDays) {
        this(itemId, warehouseId, safetyStock, reorderPoint, leadTimeDays, false, false);
    }
}
