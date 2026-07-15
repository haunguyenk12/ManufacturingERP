package com.erp.manufacturing.module.bom.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record BomLineUpdateRequest(
        @NotNull UUID componentItemId,
        @NotNull @Positive Integer lineNo,
        @NotNull @Positive BigDecimal quantityPer,
        @NotNull @DecimalMin("0.0") @DecimalMax(value = "1.0", inclusive = false) BigDecimal scrapRate
) {}
