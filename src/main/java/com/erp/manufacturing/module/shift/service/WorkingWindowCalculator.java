package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.ShiftBreak;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.WorkCalendarException;
import com.erp.manufacturing.module.shift.domain.WorkCalendarWeeklyShift;
import com.erp.manufacturing.module.shift.domain.Weekday;

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
