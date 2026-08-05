package com.erp.manufacturing.module.workorder.dto.capacity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ScheduleAdjustmentRequest(
        @NotNull Instant plannedStartAt,
        @NotNull Instant plannedEndAt,
        @NotBlank String reason,
        @NotNull Long expectedVersion
) {}
