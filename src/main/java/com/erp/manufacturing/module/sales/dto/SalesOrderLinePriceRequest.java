package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** One line of {@code POST …/commercial-prices}: VND, 0 to 999 999 999, at most 6 decimals (FE §3.2). */
public record SalesOrderLinePriceRequest(
        @NotNull UUID salesOrderLineId,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer = 9, fraction = 6) BigDecimal unitPrice
) {}
