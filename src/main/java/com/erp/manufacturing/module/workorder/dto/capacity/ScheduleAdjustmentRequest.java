package com.erp.manufacturing.module.workorder.dto.capacity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * {@code plannedEndAt} is optional since G5: absent, the server computes it from the operation's own
 * minutes over its work center's calendar, the way release does. {@code reason} needs at least 5
 * characters (FE §1.3 step 7).
 */
public record ScheduleAdjustmentRequest(
        @NotNull Instant plannedStartAt,
        Instant plannedEndAt,
        @NotBlank @Size(min = 5, max = 1000) String reason,
        @NotNull Long expectedVersion
) {}
