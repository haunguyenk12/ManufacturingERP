package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkOrderOperationResponse(
        UUID workOrderOperationId,
        UUID sourceRoutingOperationId,
        Integer sequence,
        String name,
        String workCenterCode,
        UUID workCenterId,
        BigDecimal setupMinutes,
        BigDecimal runMinutesPerUnit,
        Instant plannedStartAt,
        Instant plannedEndAt,
        Long version,
        /**
         * G8a: working minutes the operation needs — {@code ceil(setup + run × planned quantity)}, the
         * figure release schedules with. It is work time, not wall time: on a calendar the window from
         * {@code plannedStartAt} to {@code plannedEndAt} also spans breaks and off-shift hours.
         */
        Long workMinutes
) {}
