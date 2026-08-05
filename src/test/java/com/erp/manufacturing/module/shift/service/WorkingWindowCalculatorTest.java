package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // -- advance() (C2-8 forward-scheduling primitive) --------------------------------------------

    @Test
    @DisplayName("advance: a duration that fits within the same day's interval returns start + duration")
    void advance_durationFitsWithinTheSameDay_returnsStartPlusDuration() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY, shift(LocalTime.of(8, 0), LocalTime.of(17, 0)));
        LocalDateTime earliestStart = monday.atTime(9, 0);

        LocalDateTime end = WorkingWindowCalculator.advance(calendar, earliestStart, 60);

        assertThat(end).isEqualTo(monday.atTime(10, 0));
    }

    @Test
    @DisplayName("advance: zero duration returns earliestStart unchanged, without consulting the calendar")
    void advance_zeroDuration_returnsEarliestStartUnchanged() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY, shift(LocalTime.of(8, 0), LocalTime.of(17, 0)));
        LocalDateTime earliestStart = monday.atTime(9, 0);

        assertThat(WorkingWindowCalculator.advance(calendar, earliestStart, 0)).isEqualTo(earliestStart);
    }

    @Test
    @DisplayName("advance: a duration too big for one day continues into the next working day")
    void advance_spansMultipleDays_continuesOnTheNextWorkingDay() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        WorkCalendar calendar = WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(plant())
                .code("CAL").name("Calendar").effectiveFrom(LocalDate.of(2026, 1, 1))
                .status(OrganizationStatus.ACTIVE).build();
        Shift shift = shift(LocalTime.of(8, 0), LocalTime.of(12, 0)); // 4h/day
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar).weekday(Weekday.MONDAY).shift(shift).build());
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar).weekday(Weekday.TUESDAY).shift(shift).build());
        LocalDateTime earliestStart = monday.atTime(8, 0);

        // Monday gives 240 min (08:00-12:00); the remaining 60 min lands on Tuesday from 08:00.
        LocalDateTime end = WorkingWindowCalculator.advance(calendar, earliestStart, 300);

        assertThat(end).isEqualTo(monday.plusDays(1).atTime(9, 0));
    }

    @Test
    @DisplayName("advance: a NON_WORKING exception day in between is skipped entirely")
    void advance_spansANonWorkingExceptionDay_skipsIt() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        LocalDate tuesday = monday.plusDays(1);
        LocalDate wednesday = monday.plusDays(2);
        WorkCalendar calendar = WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(plant())
                .code("CAL").name("Calendar").effectiveFrom(LocalDate.of(2026, 1, 1))
                .status(OrganizationStatus.ACTIVE).build();
        Shift shift = shift(LocalTime.of(8, 0), LocalTime.of(9, 0)); // 1h/day
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar).weekday(Weekday.MONDAY).shift(shift).build());
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar).weekday(Weekday.TUESDAY).shift(shift).build());
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar).weekday(Weekday.WEDNESDAY).shift(shift).build());
        calendar.getExceptions().add(WorkCalendarException.builder()
                .workCalendar(calendar).exceptionDate(tuesday).reason("Holiday").build());

        // Monday gives 60 min (exactly the duration) — assert the walk past a fully-consumed Monday
        // into an exception Tuesday still lands correctly by asking for a little more than Monday alone.
        LocalDateTime end = WorkingWindowCalculator.advance(calendar, monday.atTime(8, 0), 90);

        assertThat(end).isEqualTo(wednesday.atTime(8, 30));
    }

    @Test
    @DisplayName("advance: days with no shift assigned (a weekend gap) are skipped")
    void advance_spansAWeekendGap_skipsDaysWithNoShiftAssigned() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        LocalDate nextMonday = monday.plusDays(7);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY, shift(LocalTime.of(8, 0), LocalTime.of(9, 0)));

        // Monday's 60 min is entirely consumed; nothing runs Tue-Sun, so the remainder lands on the
        // following Monday.
        LocalDateTime end = WorkingWindowCalculator.advance(calendar, monday.atTime(8, 0), 90);

        assertThat(end).isEqualTo(nextMonday.atTime(8, 30));
    }

    @Test
    @DisplayName("advance: an overnight shift is clipped to earliestStart, not its own interval start")
    void advance_overnightShift_clipsToEarliestStartWithinTheInterval() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        WorkCalendar calendar = calendarWithWeekday(Weekday.MONDAY, shift(LocalTime.of(22, 0), LocalTime.of(6, 0)));
        LocalDateTime earliestStart = monday.atTime(23, 0); // 1h into the shift

        LocalDateTime end = WorkingWindowCalculator.advance(calendar, earliestStart, 60);

        assertThat(end).isEqualTo(monday.plusDays(1).atTime(0, 0));
    }

    @Test
    @DisplayName("advance: a calendar with no working time in the horizon throws rather than looping forever")
    void advance_horizonExhausted_throws() {
        WorkCalendar calendar = WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(plant())
                .code("CAL").name("Calendar")
                .effectiveFrom(LocalDate.of(2020, 1, 1)).effectiveTo(LocalDate.of(2020, 1, 31))
                .status(OrganizationStatus.ACTIVE).build();
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar).weekday(Weekday.MONDAY)
                .shift(shift(LocalTime.of(8, 0), LocalTime.of(17, 0))).build());
        LocalDateTime earliestStart = LocalDate.of(2026, 1, 5).atTime(8, 0);

        assertThatThrownBy(() -> WorkingWindowCalculator.advance(calendar, earliestStart, 60))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
    }
}
