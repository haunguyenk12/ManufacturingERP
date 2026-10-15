package com.erp.manufacturing.module.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code POST /v1/inventory/counts} (G4-3). {@code countedQuantity} is the absolute quantity found on
 * the shelf, not a delta — the existing {@code POST /adjust} keeps its signed-delta meaning.
 * {@code expectedVersion} is the {@code version} of the position ({@code StockBalanceResponse}) the
 * user looked at; a newer movement on that position makes the count stale.
 */
public record StockCountRequest(
        @NotNull UUID itemId,
        @NotNull UUID warehouseId,
        UUID lotId,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal countedQuantity,
        @NotNull Long expectedVersion,
        @NotBlank @Size(max = 1000) String reason
) {}
