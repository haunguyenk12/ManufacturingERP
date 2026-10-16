package com.erp.manufacturing.module.workorder.dto.capacity;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A what-if schedule (G5, FE §3.6): the ACTIVE routing for {@code quantity} units starting at
 * {@code requestedStart}, measured against {@code [periodStart, periodEnd]} (plant timezone, inclusive,
 * at most 90 days). Nothing is written.
 */
public record CapacityPreviewRequest(
        @NotNull UUID routingId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity,
        @NotNull Instant requestedStart,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) {}
