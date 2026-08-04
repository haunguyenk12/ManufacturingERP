package com.erp.manufacturing.module.workorder.dto.variance;

import java.math.BigDecimal;

/**
 * {@code plannedMinutes} sums {@code setupMinutes + runMinutesPerUnit * plannedQuantity} across the
 * work order's frozen {@code WorkOrderOperation} snapshot (B56). {@code actualMinutes} sums
 * {@code Duration.between(actualStartedAt, actualEndedAt)} across every {@code ProductionExecution} —
 * an execution still in progress ({@code actualEndedAt == null}) is excluded, not counted as zero,
 * since partial elapsed time is not time actually spent yet.
 */
public record WorkOrderTimeVarianceResponse(
        BigDecimal plannedMinutes,
        BigDecimal actualMinutes,
        BigDecimal varianceMinutes
) {}
