package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * G7c: one purchase QC decision on a goods receipt line. {@code expectedVersion} is the receipt's
 * version. Both quantities may be {@code 0} but not together; a rejected quantity needs a reason.
 */
public record PurchaseQcDecisionRequest(
        @NotNull Long expectedVersion,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal acceptedQuantity,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal rejectedQuantity,
        @Size(max = 500) String reason
) {}
