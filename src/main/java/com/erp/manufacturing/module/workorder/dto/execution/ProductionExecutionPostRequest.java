package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One shop-floor report (spec §5.2). All three quantities are optional individually but at least
 * one must be positive — validated in the service so the failure carries a business error code.
 *
 * <p>Both timestamps are mandatory (spec §5.1 marks them "Bắt buộc", F7): a production report without
 * a time window cannot be reconciled against a shift, and the pair is what any later cycle-time or
 * OEE reporting has to read. Their <em>ordering</em> is checked separately in
 * {@code ProductionExecutionService.ensureChronological}.
 */
public record ProductionExecutionPostRequest(
        UUID workOrderOperationId,
        @PositiveOrZero BigDecimal goodQuantity,
        @PositiveOrZero BigDecimal scrapQuantity,
        @PositiveOrZero BigDecimal reworkQuantity,
        @NotNull Instant actualStartedAt,
        @NotNull Instant actualEndedAt,
        String notes
) {}
