package com.erp.manufacturing.module.bom.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code componentQuantity} is the quantity for the BOM's {@code outputQuantity}, not per unit. The
 * server stores {@code quantityPer = componentQuantity / outputQuantity} and refuses a pair whose
 * quotient needs more than 6 decimals rather than rounding it (FE Q2). A line keeps its id when the
 * same component was already on the draft.
 */
public record BomDraftLineRequest(
        @NotNull @Positive Integer lineNo,
        @NotNull UUID componentItemId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal componentQuantity,
        @NotNull @DecimalMin("0.0") @DecimalMax(value = "1.0", inclusive = false)
        @Digits(integer = 1, fraction = 6) BigDecimal scrapRate
) {}
