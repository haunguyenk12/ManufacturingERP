package com.erp.manufacturing.module.costing.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ItemStandardCostRequest(
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal materialCost,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal laborCost,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal overheadCost
) {}
