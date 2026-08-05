package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.ShiftBreak;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.WorkCalendarException;
import com.erp.manufacturing.module.shift.domain.WorkCalendarWeeklyShift;
import com.erp.manufacturing.module.shift.domain.Weekday;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Net working window computation (C2-7 Part D, {@code NEXT_PHASE_PLAN.md} §Phần D) — internal only,
 * no public endpoint (decision §1.4). {@code C2-8} (Capacity Board) is the first real caller, via
 * {@code WorkCalendarLookupService.computeWorkingWindows}.
 *
 * <p>Pure logic, no Spring/DB dependency: takes already-loaded entities and returns plain records,
 * so it is directly unit-testable without a persistence context.
 */
public final class WorkingWindowCalculator {

    private WorkingWindowCalculator() {
    }

    /**
     * Working intervals for one calendar date, breaks already subtracted. Empty (not an error) when
     * the date falls outside {@code [effectiveFrom, effectiveTo]}, is a {@link WorkCalendarException}
     * date, or no shift is assigned to that weekday.
     *
     * <p>An overnight shift assigned to this date's weekday produces an interval that <em>ends</em>
     * on the following calendar date (NEXT_PHASE_PLAN.md C2-7 §Phần D #2: a shift crossing midnight
     * is attributed to the weekday it starts on).
     */
    public static List<WorkingInterval> computeDay(WorkCalendar calendar, LocalDate date) {
        if (calendar.getEffectiveFrom() != null && date.isBefore(calendar.getEffectiveFrom())) {
            return List.of();
        }
        if (calendar.getEffectiveTo() != null && date.isAfter(calendar.getEffectiveTo())) {
            return List.of();
        }
        boolean isNonWorkingException = calendar.getExceptions().stream()
                .map(WorkCalendarException::getExceptionDate)
                .anyMatch(date::equals);
        if (isNonWorkingException) {
            return List.of();
        }

        Weekday weekday = Weekday.from(date.getDayOfWeek());
        List<WorkingInterval> intervals = new ArrayList<>();
        for (WorkCalendarWeeklyShift weeklyShift : calendar.getWeeklyShifts()) {
            if (weeklyShift.getWeekday() != weekday) {
                continue;
            }
            intervals.addAll(intervalsForShift(weeklyShift.getShift(), date));
        }
        return intervals;
    }

    private static List<WorkingInterval> intervalsForShift(Shift shift, LocalDate date) {
        LocalDateTime shiftStart = date.atTime(shift.getStartTime());
        LocalDateTime shiftEnd = shiftStart.plusMinutes(
                ShiftTimeWindow.durationMinutes(shift.getStartTime(), shift.getEndTime()));

        List<WorkingInterval> remaining = new ArrayList<>();
        remaining.add(new WorkingInterval(shiftStart, shiftEnd));

        for (ShiftBreak shiftBreak : shift.getBreaks()) {
            int breakStartOffset = ShiftTimeWindow.durationMinutes(shift.getStartTime(), shiftBreak.getStartTime());
            int breakDuration = ShiftTimeWindow.durationMinutes(shiftBreak.getStartTime(), shiftBreak.getEndTime());
            LocalDateTime breakStart = shiftStart.plusMinutes(breakStartOffset);
            LocalDateTime breakEnd = breakStart.plusMinutes(breakDuration);
            remaining = subtract(remaining, breakStart, breakEnd);
        }
        return remaining;
    }

    /**
     * Forward-schedules {@code durationMinutes} of work starting no earlier than
     * {@code earliestStart}, walking the calendar day by day and skipping non-working time (C2-8,
     * {@code NEXT_PHASE_PLAN.md} §"module/shift — new forward-scheduling primitive"). This is the
     * "infinite capacity" scheduling primitive: it only asks "when does the calendar have this much
     * working time free", never "is another work order already using it" — that comparison is the
     * Capacity Board's job, a separate read over already-persisted schedules.
     *
     * <p>The first day's intervals are clipped to start no earlier than {@code earliestStart} (an
     * operation cannot be scheduled into the past relative to its own anchor). Capped at
     * {@link #MAX_HORIZON_DAYS} days to fail loudly against a pathological calendar (e.g. one whose
     * {@code effectiveTo} is already in the past) instead of looping forever.
     */
    public static LocalDateTime advance(WorkCalendar calendar, LocalDateTime earliestStart, long durationMinutes) {
        if (durationMinutes <= 0) {
            return earliestStart;
        }
        long remaining = durationMinutes;
        LocalDate date = earliestStart.toLocalDate();
        for (int daysWalked = 0; daysWalked <= MAX_HORIZON_DAYS; daysWalked++, date = date.plusDays(1)) {
            for (WorkingInterval interval : computeDay(calendar, date)) {
                LocalDateTime intervalStart = interval.start().isBefore(earliestStart) ? earliestStart : interval.start();
                if (!intervalStart.isBefore(interval.end())) {
                    continue;
                }
                long availableMinutes = Duration.between(intervalStart, interval.end()).toMinutes();
                if (availableMinutes >= remaining) {
                    return intervalStart.plusMinutes(remaining);
                }
                remaining -= availableMinutes;
            }
        }
        throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                "Work calendar " + calendar.getWorkCalendarId() + " has no working time within "
                        + MAX_HORIZON_DAYS + " days of " + earliestStart);
    }

    private static final int MAX_HORIZON_DAYS = 3650;

    private static List<WorkingInterval> subtract(List<WorkingInterval> pieces, LocalDateTime breakStart, LocalDateTime breakEnd) {
        List<WorkingInterval> result = new ArrayList<>();
        for (WorkingInterval piece : pieces) {
            if (!breakEnd.isAfter(piece.start()) || !breakStart.isBefore(piece.end())) {
                result.add(piece);
                continue;
            }
            if (breakStart.isAfter(piece.start())) {
                result.add(new WorkingInterval(piece.start(), breakStart));
            }
            if (breakEnd.isBefore(piece.end())) {
                result.add(new WorkingInterval(breakEnd, piece.end()));
            }
        }
        return result;
    }
}
