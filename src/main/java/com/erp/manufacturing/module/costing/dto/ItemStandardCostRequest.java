package com.erp.manufacturing.module.costing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ItemStandardCostRequest(
        @NotNull @PositiveOrZero BigDecimal materialCost,
        @NotNull @PositiveOrZero BigDecimal laborCost,
        @NotNull @PositiveOrZero BigDecimal overheadCost
) {}
