package com.erp.manufacturing.module.workorder.dto.schedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What release would commit for this work order now (G8a, D9) — computed by the same
 * {@code WorkOrderScheduler} release uses, against the calendars as they are now. Nothing is written.
 */
public record WorkOrderSchedulePreviewResponse(
        UUID workOrderId,
        Long version,
        String timeZone,
        /** Fingerprint of the calendars consulted; release records the same value when nothing changed. */
        String calendarRevision,
        BigDecimal quantity,
        Instant plannedStartAt,
        List<Row> rows,
        List<Warning> warnings,
        Instant generatedAt
) {

    /**
     * @param workMinutes           working minutes needed — not wall time; the window can span breaks
     * @param onCalendar            {@code false}: placed in continuous time, the work center has no calendar
     * @param overlappingOperations operations of other work orders already scheduled on the same work
     *                              center inside this window — advice, never a block
     */
    public record Row(UUID workOrderOperationId, Integer sequence, String name, UUID workCenterId,
                      String workCenterCode, Instant startsAt, Instant endsAt, long workMinutes,
                      boolean onCalendar, int overlappingOperations) {}

    /** {@code code}: {@code NO_ROUTING}, {@code NO_WORK_CENTER}, {@code NO_CALENDAR} or {@code OVERLAP}. */
    public record Warning(String code, UUID workOrderOperationId, String message) {}
}
