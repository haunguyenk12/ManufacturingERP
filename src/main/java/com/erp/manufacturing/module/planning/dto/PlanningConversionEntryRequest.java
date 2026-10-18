package com.erp.manufacturing.module.planning.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanningConversionEntryRequest(
        @NotNull UUID requirementLineId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity
) {}
