package com.erp.manufacturing.module.billing.dto;

import com.erp.manufacturing.module.billing.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** {@code PATCH /v1/customer-payments/{id}} (G10d): full replacement of a DRAFT; the customer does not change. */
public record CustomerPaymentUpdateRequest(
        @NotNull Long expectedVersion,
        @NotNull LocalDate documentDate,
        @NotNull PaymentMethod method,
        @Size(max = 200) String reference,
        @Size(max = 2000) String note,
        @NotNull @Positive @DecimalMax("9007199254740991") @Digits(integer = 16, fraction = 0) BigDecimal amount,
        @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull CustomerPaymentAllocationRequest> allocations
) {}
