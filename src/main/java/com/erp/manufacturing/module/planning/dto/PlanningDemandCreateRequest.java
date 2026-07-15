package com.erp.manufacturing.module.planning.dto;

import com.erp.manufacturing.module.planning.domain.PlanningDemandType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PlanningDemandCreateRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        @NotNull UUID itemId,
        UUID warehouseId,
        PlanningDemandType demandType,
        @NotNull @Positive BigDecimal requiredQuantity,
        @NotNull LocalDate dueDate,
        @PositiveOrZero Integer priority,
        @Size(max = 80) String referenceType,
        @Size(max = 120) String referenceId
) {}
