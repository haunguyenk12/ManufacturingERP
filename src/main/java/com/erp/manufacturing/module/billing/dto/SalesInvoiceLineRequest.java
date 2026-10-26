package com.erp.manufacturing.module.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** One invoice line: a line of a POSTED delivery of the same order, and how much of it to bill (FE R11). */
public record SalesInvoiceLineRequest(
        @NotNull UUID deliveryLineId,
        @NotNull @Positive @DecimalMax("999999999") @Digits(integer = 9, fraction = 6) BigDecimal quantity
) {}
