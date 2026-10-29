package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseOrderLineRequest(
        UUID purchaseRequisitionLineId,
        @NotNull UUID itemId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal orderedQuantity,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal unitPrice,
        @Size(min = 3, max = 3) String currencyCode,
        LocalDate expectedDate
) {}
