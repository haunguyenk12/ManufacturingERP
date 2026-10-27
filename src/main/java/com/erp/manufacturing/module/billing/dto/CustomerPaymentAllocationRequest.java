package com.erp.manufacturing.module.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** One allocation: a POSTED invoice at the version the user saw, and a whole-dong amount > 0 (FE R16). */
public record CustomerPaymentAllocationRequest(
        @NotNull UUID invoiceId,
        @NotNull Long expectedInvoiceVersion,
        @NotNull @Positive @DecimalMax("9007199254740991") @Digits(integer = 16, fraction = 0) BigDecimal amount
) {}
