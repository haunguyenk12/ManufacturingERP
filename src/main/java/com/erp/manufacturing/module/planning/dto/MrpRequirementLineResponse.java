package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MrpRequirementLineResponse(
        UUID mrpRequirementLineId,
        UUID mrpRunId,
        UUID parentRequirementLineId,
        UUID sourceDemandId,
        UUID itemId,
        String itemCode,
        String itemName,
        UUID warehouseId,
        String warehouseCode,
        Integer requirementLevel,
        BigDecimal grossRequiredQuantity,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        BigDecimal openSupplyQuantity,
        BigDecimal safetyStockQuantity,
        BigDecimal netRequiredQuantity,
        LocalDate dueDate,
        String requirementStatus,
        String note,
        Instant createdAt
) {}
