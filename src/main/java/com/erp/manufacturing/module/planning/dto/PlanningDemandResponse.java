package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlanningDemandResponse(
        UUID planningDemandId,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        UUID itemId,
        String itemSku,
        String itemName,
        UUID warehouseId,
        String warehouseCode,
        String demandType,
        BigDecimal requiredQuantity,
        LocalDate dueDate,
        Integer priority,
        String status,
        String referenceType,
        String referenceId,
        Instant createdAt,
        Instant updatedAt
) {}
