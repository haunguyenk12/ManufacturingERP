package com.erp.manufacturing.module.workorder.dto.capacity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Load of every work center of a plant over {@code [periodStart, periodEnd]} in the plant's timezone (G5). */
public record CapacityPeriodResponse(
        UUID plantId,
        String timezone,
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant generatedAt,
        List<CapacityLoadResponse> loads
) {}
