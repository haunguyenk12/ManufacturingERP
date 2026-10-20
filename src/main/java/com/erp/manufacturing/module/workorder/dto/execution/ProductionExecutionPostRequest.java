package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One shop-floor report (spec §5.2). All three quantities are optional individually but at least
 * one must be positive — validated in the service so the failure carries a business error code.
 *
 * <p>Both timestamps are mandatory (spec §5.1 marks them "Bắt buộc", F7): a production report without
 * a time window cannot be reconciled against a shift, and the pair is what any later cycle-time or
 * OEE reporting has to read. Their <em>ordering</em> is checked separately in
 * {@code ProductionExecutionService.ensureChronological}.
 *
 * <p>G8c: {@code expectedWorkOrderVersion} is required (D5) — checked in the service so a missing one is a 400 on
 * that field. {@code sourceExecutionId} marks a retry of an earlier execution's rework (D7); {@code consumption}
 * records material actually used (D4), may be empty, at most 500 entries.
 */
public record ProductionExecutionPostRequest(
        UUID workOrderOperationId,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal goodQuantity,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal scrapQuantity,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal reworkQuantity,
        @NotNull Instant actualStartedAt,
        @NotNull Instant actualEndedAt,
        String notes,
        Long expectedWorkOrderVersion,
        UUID sourceExecutionId,
        @Size(max = 500) List<@Valid ExecutionConsumptionRequest> consumption
) {

    /** Shape before G8c. */
    public ProductionExecutionPostRequest(UUID workOrderOperationId, BigDecimal goodQuantity, BigDecimal scrapQuantity,
                                          BigDecimal reworkQuantity, Instant actualStartedAt, Instant actualEndedAt,
                                          String notes) {
        this(workOrderOperationId, goodQuantity, scrapQuantity, reworkQuantity, actualStartedAt, actualEndedAt, notes,
                null, null, null);
    }

    /** Never null for the service: an absent array is the same as an empty one (FE Q18). */
    public List<ExecutionConsumptionRequest> consumptionOrEmpty() {
        return consumption == null ? List.of() : consumption;
    }
}
