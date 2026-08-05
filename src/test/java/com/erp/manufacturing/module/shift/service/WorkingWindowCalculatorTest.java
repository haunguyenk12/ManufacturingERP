package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.ShiftBreak;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.WorkCalendarException;
import com.erp.manufacturing.module.shift.domain.WorkCalendarWeeklyShift;
import com.erp.manufacturing.module.shift.domain.Weekday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure logic — no Spring context, no DB (NEXT_PHASE_PLAN.md C2-7 §Phần D "Test bắt buộc"). Entities
 * are built directly with builders; nothing is persisted.
 */
@DisplayName("WorkingWindowCalculator")
class WorkingWindowCalculatorTest {

    private Plant plant() {
        return Plant.builder().plantId(UUID.randomUUID()).code("PLANT").name("Plant")
                .status(OrganizationStatus.ACTIVE).build();
    }

    private Shift shift(LocalTime start, LocalTime end, ShiftBreak... breaks) {
        Shift shift = Shift.builder().shiftId(UUID.randomUUID()).plant(plant()).code("SH").name("Shift")
                .startTime(start).endTime(end).status(OrganizationStatus.ACTIVE).build();
        for (ShiftBreak shiftBreak : breaks) {
            shift.getBreaks().add(shiftBreak);
        }
        return shift;
    }

    private ShiftBreak shiftBreak(LocalTime start, LocalTime end) {
        return ShiftBreak.builder().startTime(start).endTime(end).build();
    }

    private WorkCalendar calendarWithWeekday(Weekday weekday, Shift... shifts) {
        WorkCalendar calendar = WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(plant())
                .code("CAL").name("Calendar").effectiveFrom(LocalDate.of(2026, 1, 1))
                .status(OrganizationStatus.ACTIVE).build();
        for (Shift shift : shifts) {
            calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder()
                    .workCalendar(calendar).weekday(weekday).shift(shift).build());
        }
        return calendar;
    }

    @Test
    @DisplayName("a same-day shift produces one interval on the given date")
    void sameDayShift_producesOneIntervalOnTheGivenDate() {
        // 2026-01-05 is a Monday
        LocalDate monday = LocalDate.of(2026, 1, 5);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY, shift(LocalTime.of(8, 0), LocalTime.of(17, 0)));

        List<WorkingInterval> intervals = WorkingWindowCalculator.computeDay(calendar, monday);

        assertThat(intervals).containsExactly(
                new WorkingInterval(monday.atTime(8, 0), monday.atTime(17, 0)));
    }

    @Test
    @DisplayName("an overnight shift's interval ends on the following calendar date")
    void overnightShift_intervalEndsOnTheFollowingDate() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY, shift(LocalTime.of(22, 0), LocalTime.of(6, 0)));

        List<WorkingInterval> intervals = WorkingWindowCalculator.computeDay(calendar, monday);

        assertThat(intervals).containsExactly(
                new WorkingInterval(monday.atTime(22, 0), monday.plusDays(1).atTime(6, 0)));
    }

    @Test
    @DisplayName("a break in the middle of the shift splits it into two intervals")
    void breakInTheMiddle_splitsTheShiftIntoTwoIntervals() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY,
                shift(LocalTime.of(8, 0), LocalTime.of(17, 0), shiftBreak(LocalTime.of(12, 0), LocalTime.of(13, 0))));

        List<WorkingInterval> intervals = WorkingWindowCalculator.computeDay(calendar, monday);

        assertThat(intervals).containsExactly(
                new WorkingInterval(monday.atTime(8, 0), monday.atTime(12, 0)),
                new WorkingInterval(monday.atTime(13, 0), monday.atTime(17, 0)));
    }

    @Test
    @DisplayName("a break touching the shift's end boundary removes exactly the tail of the interval")
    void breakAtTheShiftBoundary_removesExactlyTheTail() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY,
                shift(LocalTime.of(8, 0), LocalTime.of(17, 0), shiftBreak(LocalTime.of(16, 0), LocalTime.of(17, 0))));

        List<WorkingInterval> intervals = WorkingWindowCalculator.computeDay(calendar, monday);

        assertThat(intervals).containsExactly(
                new WorkingInterval(monday.atTime(8, 0), monday.atTime(16, 0)));
    }

    @Test
    @DisplayName("a date marked NON_WORKING by a calendar exception produces no intervals")
    void nonWorkingException_producesNoIntervals() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY, shift(LocalTime.of(8, 0), LocalTime.of(17, 0)));
        calendar.getExceptions().add(WorkCalendarException.builder()
                .workCalendar(calendar).exceptionDate(monday).reason("Holiday").build());

        List<WorkingInterval> intervals = WorkingWindowCalculator.computeDay(calendar, monday);

        assertThat(intervals).isEmpty();
    }

    @Test
    @DisplayName("a date outside [effectiveFrom, effectiveTo] produces no intervals, not an error")
    void dateOutsideEffectiveRange_producesNoIntervals() {
        WorkCalendar calendar = WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(plant())
                .code("CAL").name("Calendar")
                .effectiveFrom(LocalDate.of(2026, 1, 1)).effectiveTo(LocalDate.of(2026, 1, 31))
                .status(OrganizationStatus.ACTIVE).build();
        Shift shift = shift(LocalTime.of(8, 0), LocalTime.of(17, 0));
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar).weekday(Weekday.SUNDAY).shift(shift).build());

        assertThat(WorkingWindowCalculator.computeDay(calendar, LocalDate.of(2025, 12, 28))).isEmpty();
        assertThat(WorkingWindowCalculator.computeDay(calendar, LocalDate.of(2026, 2, 1))).isEmpty();
    }

    @Test
    @DisplayName("a weekday with no shift assigned returns empty, not an error")
    void weekdayWithNoShiftAssigned_returnsEmpty() {
        LocalDate tuesday = LocalDate.of(2026, 1, 6);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY, shift(LocalTime.of(8, 0), LocalTime.of(17, 0)));

        assertThat(WorkingWindowCalculator.computeDay(calendar, tuesday)).isEmpty();
    }
}
