package com.erp.manufacturing.module.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record StockAdjustRequest(
        @NotNull UUID itemId,
        @NotNull UUID warehouseId,
        UUID lotId,
        @Size(max = 120) String lotCode,
        @NotNull BigDecimal quantityDelta,
        @Size(max = 1000) String reason,
        @Size(max = 80) String referenceType,
        @Size(max = 120) String referenceId
) {}
