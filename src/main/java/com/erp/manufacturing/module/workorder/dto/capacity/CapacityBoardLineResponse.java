package com.erp.manufacturing.module.workorder.dto.capacity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One scheduled operation on the Capacity Board (C2-8), enriched with its Work Center's day
 * capacity/load/utilization context. {@code dayCapacityMinutes}/{@code utilizationPercent} are
 * {@code null} when the operation's Work Center has no {@code workCalendar} attached — there is no
 * data to compute capacity from, which is different from a calendar that computes to zero.
 */
public record CapacityBoardLineResponse(
        UUID workOrderOperationId,
        Integer sequence,
        String operationName,
        UUID workOrderId,
        String workOrderNo,
        String workOrderStatus,
        UUID workCenterId,
        String workCenterCode,
        String workCenterName,
        UUID plantId,
        String plantCode,
        Instant plannedStartAt,
        Instant plannedEndAt,
        BigDecimal setupMinutes,
        BigDecimal runMinutesPerUnit,
        BigDecimal operationMinutes,
        BigDecimal dayCapacityMinutes,
        BigDecimal dayExistingLoadMinutes,
        BigDecimal utilizationPercent,
        boolean overload,
        boolean calendarExceptionApplies,
        Long version
) {}
