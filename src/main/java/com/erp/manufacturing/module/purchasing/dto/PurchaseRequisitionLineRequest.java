package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseRequisitionLineRequest(
        @NotNull UUID itemId,
        UUID supplierId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal requestedQuantity,
        LocalDate neededByDate,
        @Size(max = 2000) String note
) {}
