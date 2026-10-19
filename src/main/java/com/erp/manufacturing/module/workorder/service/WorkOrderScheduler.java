package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The one scheduling formula of a work order (C2-8 B89, G8a): sequential forward scheduling per
 * operation in sequence order, each starting when the previous one ends, walking the operation's work
 * center calendar to skip non-working time. Release commits what this computes; the work order's
 * schedule preview shows exactly the same thing without committing it — so the preview can never
 * promise a schedule release would not produce.
 *
 * <p>This is "infinite capacity" scheduling — it does not wait for other work orders' load on the same
 * work center; the Capacity Board and the preview's overlap warning report that load instead. Unlike the
 * plant capacity preview (G5) it does not divide by {@code capacityUnits}: release never has
 * (CLAUDE.md §0.53 #6), and the preview of a work order must match its release.
 *
 * <p>An operation whose work center has no calendar gets continuous time (nothing to consult); one with
 * no work center at all (routing rows predating C2-6) is placed in continuous time too and stays
 * invisible to the Capacity Board.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderScheduler {

    private final WorkCalendarLookupService workCalendarLookupService;

    /**
     * @param onCalendar {@code false} when the operation was placed in continuous time because its work
     *                   center has no calendar (or there is no work center)
     */
    public record Slot(WorkOrderOperation operation, Instant start, Instant end, long workMinutes,
                       boolean onCalendar) {}

    public record Schedule(List<Slot> slots, String calendarRevision, String timeZone) {}

    public Schedule compute(WorkOrder workOrder, Instant start, BigDecimal quantity) {
        String timeZone = workOrder.getPlant().getTimezone();
        ZoneId zone = ZoneId.of(timeZone);
        Set<UUID> calendarIds = new LinkedHashSet<>();
        List<Slot> slots = new ArrayList<>();
        Instant cursor = start;
        for (WorkOrderOperation operation : workOrder.getOperations()) {
            long workMinutes = operation.workMinutes(quantity);
            WorkCenter workCenter = operation.getWorkCenter();
            boolean onCalendar = workCenter != null && workCenter.getWorkCalendar() != null;
            Instant end;
            if (onCalendar) {
                UUID calendarId = workCenter.getWorkCalendar().getWorkCalendarId();
                calendarIds.add(calendarId);
                end = workCalendarLookupService.computeEndInstant(calendarId, cursor, zone, workMinutes);
            } else {
                end = cursor.plus(workMinutes, ChronoUnit.MINUTES);
            }
            slots.add(new Slot(operation, cursor, end, workMinutes, onCalendar));
            cursor = end;
        }
        // No calendar consulted ⇒ nothing to fingerprint, and the calendar module is never asked.
        String calendarRevision = calendarIds.isEmpty() ? null : workCalendarLookupService.calendarRevision(calendarIds);
        return new Schedule(slots, calendarRevision, timeZone);
    }
}
