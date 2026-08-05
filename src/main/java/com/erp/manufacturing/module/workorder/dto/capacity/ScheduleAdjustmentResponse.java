package com.erp.manufacturing.module.workorder.dto.capacity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of a manual schedule override (C2-8 decision #3): the write always succeeds once the
 * version/time-range checks pass — {@code sequenceConflict}/{@code calendarConflict}/
 * {@code capacityOverload} are advisory flags for the manager to see and resolve by hand, not
 * reasons the request was rejected. Backend never auto-shifts sibling operations.
 */
public record ScheduleAdjustmentResponse(
        UUID workOrderOperationId,
        Instant plannedStartAt,
        Instant plannedEndAt,
        String scheduleAdjustmentReason,
        Long version,
        boolean sequenceConflict,
        boolean calendarConflict,
        boolean capacityOverload,
        BigDecimal dayCapacityMinutes,
        BigDecimal dayExistingLoadMinutes,
        BigDecimal utilizationPercent
) {}
