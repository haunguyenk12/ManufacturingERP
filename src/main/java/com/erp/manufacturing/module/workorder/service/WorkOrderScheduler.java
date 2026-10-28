package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.shift.service.TimeInterval;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.service.query.CapacityPlacement;
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
 * The one scheduling formula of a work order (C2-8 B89, G8a, CF-5 B269): sequential forward scheduling per
 * operation in sequence order, each starting when the previous one ends, walking the operation's work
 * center calendar to skip non-working time. Release commits what this computes; the work order's
 * schedule preview shows exactly the same thing without committing it — so the preview can never
 * promise a schedule release would not produce.
 *
 * <p>The walk is {@link CapacityPlacement}, the same one the plant capacity preview (G5) uses: the work
 * center's {@code capacityUnits} work in parallel, so an operation needing {@code required} capacity minutes
 * takes {@code required / capacityUnits} working minutes, and the last partial window ends on the second
 * ({@code ceil}). Before CF-5 release did not divide and walked another calendar routine, so the plant
 * preview promised shorter schedules than release committed (CLAUDE.md §0.53 #6).
 *
 * <p>This is "infinite capacity" scheduling — it does not wait for other work orders' load on the same
 * work center; the Capacity Board and the preview's overlap warning report that load instead.
 *
 * <p>An operation whose work center has no calendar gets continuous time (nothing to consult); one with
 * no work center at all (routing rows predating C2-6) is placed in continuous time with one unit and stays
 * invisible to the Capacity Board.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderScheduler {

    /** Working time is fetched this many days at a time while an operation still needs more. */
    static final int WINDOW_CHUNK_DAYS = 31;
    /** Same horizon the calendar walk always had (WorkingWindowCalculator.MAX_HORIZON_DAYS). */
    static final int MAX_HORIZON_DAYS = 3650;

    private final WorkCalendarLookupService workCalendarLookupService;

    /**
     * @param workMinutes          {@code ceil(required)} — the work-time figure the API has shown since G8a
     * @param capacityUnits        units of the work center at this moment (1 without a work center)
     * @param requiredMinutes      {@code setup + run × quantity}, capacity minutes, scale 6
     * @param activeElapsedMinutes {@code required / capacityUnits}, working minutes, scale 6
     * @param onCalendar           {@code false} when the operation was placed in continuous time because its work
     *                             center has no calendar (or there is no work center)
     */
    public record Slot(WorkOrderOperation operation, Instant start, Instant end, long workMinutes,
                       boolean onCalendar, int capacityUnits, BigDecimal requiredMinutes,
                       BigDecimal activeElapsedMinutes) {}

    public record Schedule(List<Slot> slots, String calendarRevision, String timeZone) {}

    public Schedule compute(WorkOrder workOrder, Instant start, BigDecimal quantity) {
        String timeZone = workOrder.getPlant().getTimezone();
        ZoneId zone = ZoneId.of(timeZone);
        Set<UUID> calendarIds = new LinkedHashSet<>();
        List<Slot> slots = new ArrayList<>();
        Instant cursor = start;
        for (WorkOrderOperation operation : workOrder.getOperations()) {
            WorkCenter workCenter = operation.getWorkCenter();
            int units = workCenter == null ? 1 : workCenter.getCapacityUnits();
            BigDecimal required = CapacityPlacement.requiredMinutes(
                    operation.getSetupMinutes(), operation.getRunMinutesPerUnit(), quantity);
            boolean onCalendar = workCenter != null && workCenter.getWorkCalendar() != null;
            if (onCalendar) {
                calendarIds.add(workCenter.getWorkCalendar().getWorkCalendarId());
            }
            Placed placed = place(workCenter, cursor, zone, required, units);
            slots.add(new Slot(operation, placed.start(), placed.end(), operation.workMinutes(quantity), onCalendar,
                    units, required, CapacityPlacement.activeElapsedMinutes(required, units)));
            cursor = placed.end();
        }
        // No calendar consulted ⇒ nothing to fingerprint, and the calendar module is never asked.
        String calendarRevision = calendarIds.isEmpty() ? null : workCalendarLookupService.calendarRevision(calendarIds);
        return new Schedule(slots, calendarRevision, timeZone);
    }

    /**
     * End of one operation started at {@code start} with {@code units} in parallel — the walk release uses, for a
     * manual schedule adjustment that leaves the end out (G5 B167).
     */
    public Instant computeEnd(WorkOrderOperation operation, Instant start, BigDecimal quantity, int units) {
        BigDecimal required = CapacityPlacement.requiredMinutes(
                operation.getSetupMinutes(), operation.getRunMinutesPerUnit(), quantity);
        ZoneId zone = ZoneId.of(operation.getWorkOrder().getPlant().getTimezone());
        return place(operation.getWorkCenter(), start, zone, required, units).end();
    }

    private record Placed(Instant start, Instant end) {}

    /** On a calendar the operation starts at its first working moment; otherwise at {@code cursor}. */
    private Placed place(WorkCenter workCenter, Instant cursor, ZoneId zone, BigDecimal required, int units) {
        if (workCenter == null || workCenter.getWorkCalendar() == null) {
            return new Placed(cursor, cursor.plusSeconds(CapacityPlacement.continuousSeconds(required, units)));
        }
        List<CapacityPlacement.Segment> segments = walkCalendar(
                workCenter.getWorkCalendar().getWorkCalendarId(), cursor, zone, required, units);
        return segments.isEmpty() ? new Placed(cursor, cursor)
                : new Placed(segments.get(0).start(), segments.get(segments.size() - 1).end());
    }

    private List<CapacityPlacement.Segment> walkCalendar(UUID calendarId, Instant cursor, ZoneId zone,
                                                         BigDecimal required, int units) {
        List<CapacityPlacement.Segment> segments = new ArrayList<>();
        BigDecimal remaining = required;
        Instant limit = cursor.plus(MAX_HORIZON_DAYS, ChronoUnit.DAYS);
        Instant from = cursor;
        while (remaining.signum() > 0) {
            if (!from.isBefore(limit)) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Work calendar " + calendarId + " has no working time within " + MAX_HORIZON_DAYS
                                + " days of " + cursor);
            }
            Instant to = from.plus(WINDOW_CHUNK_DAYS, ChronoUnit.DAYS);
            List<TimeInterval> windows = workCalendarLookupService.computeWorkingIntervals(calendarId, from, to, zone);
            CapacityPlacement.Result placed = CapacityPlacement.place(windows, from, to, null, remaining, units);
            segments.addAll(placed.segments());
            remaining = placed.remaining();
            from = to;
        }
        return segments;
    }
}
