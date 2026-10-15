package com.erp.manufacturing.module.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** One line of {@code POST /v1/inventory/transfers} (G4-2). {@code lotId} is required for lot-tracked items. */
public record StockTransferLineRequest(
        @NotNull UUID itemId,
        UUID lotId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity
) {}
