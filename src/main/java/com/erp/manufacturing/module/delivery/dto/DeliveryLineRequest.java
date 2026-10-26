package com.erp.manufacturing.module.delivery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One delivery line: a sales order line, the stock position it comes from ({@code sourceKey}, exactly as
 * {@code …/delivery-sources} issued it — FE Q12) and a positive quantity with at most 6 decimals.
 */
public record DeliveryLineRequest(
        @NotNull UUID salesOrderLineId,
        @NotBlank @Size(max = 200) String sourceKey,
        @NotNull @Positive @DecimalMax("999999999") @Digits(integer = 9, fraction = 6) BigDecimal quantity
) {}
