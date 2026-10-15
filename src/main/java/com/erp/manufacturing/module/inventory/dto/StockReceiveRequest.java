package com.erp.manufacturing.module.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code expiresAt} (G4-4) is accepted only when the receipt creates a new lot; it must be in the
 * future by the server clock. {@code @Digits(fraction = 6)}: the column is {@code NUMERIC(19,6)} and
 * Postgres would otherwise round a 7th decimal silently (R08).
 */
public record StockReceiveRequest(
        @NotNull UUID itemId,
        @NotNull UUID warehouseId,
        UUID lotId,
        @Size(max = 120) String lotCode,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity,
        @Size(max = 1000) String reason,
        @Size(max = 80) String referenceType,
        @Size(max = 120) String referenceId,
        @Future Instant expiresAt
) {}
