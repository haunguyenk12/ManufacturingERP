package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemSupplierRequest(
        UUID supplierId,
        @Size(max = 120) String supplierItemCode,
        @PositiveOrZero Integer leadTimeDays,
        @Positive BigDecimal minimumOrderQuantity,
        @PositiveOrZero BigDecimal unitPrice,
        @Size(min = 3, max = 3) String currencyCode,
        Boolean preferred
) {}
