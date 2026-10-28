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
        Long workMinutes,
        /** CF-5: the work center's {@code capacityUnits} release scheduled with; null before release / before V94. */
        Integer capturedCapacityUnits,
        /** CF-5: capacity minutes needed ({@code setup + run × planned}), as release captured them. */
        BigDecimal requiredResourceMinutes,
        /** CF-5: working minutes on the calendar that took ({@code required / capturedCapacityUnits}). */
        BigDecimal activeElapsedMinutes
) {

    /** Shape before CF-5, kept so callers that build it positionally still compile. */
    public WorkOrderOperationResponse(UUID workOrderOperationId, UUID sourceRoutingOperationId, Integer sequence,
                                      String name, String workCenterCode, UUID workCenterId, BigDecimal setupMinutes,
                                      BigDecimal runMinutesPerUnit, Instant plannedStartAt, Instant plannedEndAt,
                                      Long version, Long workMinutes) {
        this(workOrderOperationId, sourceRoutingOperationId, sequence, name, workCenterCode, workCenterId,
                setupMinutes, runMinutesPerUnit, plannedStartAt, plannedEndAt, version, workMinutes,
                null, null, null);
    }
}
