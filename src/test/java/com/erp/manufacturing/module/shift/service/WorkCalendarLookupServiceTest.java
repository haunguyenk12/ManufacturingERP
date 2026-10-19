package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.ShiftBreak;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.WorkCalendarException;
import com.erp.manufacturing.module.shift.domain.WorkCalendarWeeklyShift;
import com.erp.manufacturing.module.shift.domain.Weekday;
import com.erp.manufacturing.module.shift.repository.WorkCalendarRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G5 {@link WorkCalendarLookupService#computeWorkingIntervals}: working time as instants in the plant's
 * zone, the way the capacity board and preview see it. The FE handoff's own numbers (A21) are the
 * fixtures, so a failure here is a disagreement with the agreed example, not with an invented one.
 */
@DisplayName("WorkCalendarLookupService.computeWorkingIntervals (G5)")
class WorkCalendarLookupServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-10-13 is a Tuesday. */
    private static final LocalDate TUE = LocalDate.of(2026, 10, 13);

    private final WorkCalendarRepository repository = mock(WorkCalendarRepository.class);
    private final WorkCalendarLookupService service = new WorkCalendarLookupService(repository);

    /**
     * FE A21: shift 22:00→06:00 starting Tuesday, break 02:00–02:30. The Wednesday-only period gets the
     * shift's tail 00:00→06:00 minus the break = 330 minutes, even though no shift STARTS on Wednesday.
     */
    @Test
    void overnightShift_tailIsCountedOnTheNextDay() {
        WorkCalendar calendar = calendar(shift(LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.of(2, 0), LocalTime.of(2, 30)), Weekday.TUESDAY);

        List<TimeInterval> wednesday = intervals(calendar, TUE.plusDays(1));

        assertThat(sum(wednesday)).isEqualByComparingTo("330");
        assertThat(wednesday.get(0).start()).isEqualTo(at(TUE.plusDays(1), 0, 0));
    }

    /** FE §3.6: a holiday on the day AFTER the shift started does not cut the tail of that shift. */
    @Test
    void holidayOnTheNextDay_doesNotCutTheTailOfTheShiftStartedBefore() {
        WorkCalendar calendar = calendar(shift(LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.of(2, 0), LocalTime.of(2, 30)), Weekday.TUESDAY);
        calendar.getExceptions().add(WorkCalendarException.builder().workCalendar(calendar)
                .exceptionDate(TUE.plusDays(1)).build());

        assertThat(sum(intervals(calendar, TUE.plusDays(1)))).isEqualByComparingTo("330");
    }

    /** ... while a holiday on the day the shift starts removes the whole shift, tail included. */
    @Test
    void holidayOnTheStartDay_removesTheWholeShift() {
        WorkCalendar calendar = calendar(shift(LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.of(2, 0), LocalTime.of(2, 30)), Weekday.TUESDAY);
        calendar.getExceptions().add(WorkCalendarException.builder().workCalendar(calendar)
                .exceptionDate(TUE).build());

        assertThat(intervals(calendar, TUE.plusDays(1))).isEmpty();
    }

    /** FE A20: 08:00–17:00 with lunch 12:00–13:00 is 480 minutes in two windows. */
    @Test
    void dayShiftWithLunch_isFourHundredEightyMinutesInTwoWindows() {
        WorkCalendar calendar = calendar(shift(LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 0)), Weekday.TUESDAY);

        List<TimeInterval> tuesday = intervals(calendar, TUE);

        assertThat(tuesday).hasSize(2);
        assertThat(sum(tuesday)).isEqualByComparingTo("480");
    }

    /** Two shifts that overlap on one calendar are merged — the same minute is never counted twice. */
    @Test
    void overlappingShifts_areMerged() {
        Shift early = shift(LocalTime.of(6, 0), LocalTime.of(14, 0), null, null);
        Shift late = shift(LocalTime.of(12, 0), LocalTime.of(20, 0), null, null);
        WorkCalendar calendar = calendar(early, Weekday.TUESDAY);
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder().workCalendar(calendar)
                .weekday(Weekday.TUESDAY).shift(late).build());

        List<TimeInterval> tuesday = intervals(calendar, TUE);

        assertThat(tuesday).hasSize(1);
        assertThat(sum(tuesday)).isEqualByComparingTo("840"); // 06:00–20:00
    }

    /**
     * G8a (D9): the calendar revision is a fingerprint of content, so it stays the same while nothing a
     * schedule depends on changes — and moves when a break moves, even though a break is a child row whose
     * edit need not touch any version column.
     */
    @Test
    void calendarRevision_isStableForTheSameContent_andMovesWhenABreakMoves() {
        Shift shift = shift(LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(12, 0), LocalTime.of(13, 0));
        WorkCalendar calendar = calendar(shift, Weekday.TUESDAY);
        when(repository.findWithWeeklyShiftsByWorkCalendarId(calendar.getWorkCalendarId()))
                .thenReturn(Optional.of(calendar));

        String first = service.calendarRevision(List.of(calendar.getWorkCalendarId()));
        String again = service.calendarRevision(List.of(calendar.getWorkCalendarId()));
        shift.getBreaks().get(0).setStartTime(LocalTime.of(11, 30));
        String moved = service.calendarRevision(List.of(calendar.getWorkCalendarId()));

        assertThat(first).hasSize(16).isEqualTo(again);
        assertThat(moved).isNotEqualTo(first);
    }

    @Test
    void calendarRevision_withoutCalendars_isNull() {
        assertThat(service.calendarRevision(List.of())).isNull();
    }

    private List<TimeInterval> intervals(WorkCalendar calendar, LocalDate day) {
        when(repository.findWithWeeklyShiftsByWorkCalendarId(calendar.getWorkCalendarId()))
                .thenReturn(Optional.of(calendar));
        return service.computeWorkingIntervals(calendar.getWorkCalendarId(),
                day.atStartOfDay(ZONE).toInstant(), day.plusDays(1).atStartOfDay(ZONE).toInstant(), ZONE);
    }

    private static BigDecimal sum(List<TimeInterval> intervals) {
        return intervals.stream().map(TimeInterval::minutes).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(ZONE).toInstant();
    }

    private WorkCalendar calendar(Shift shift, Weekday weekday) {
        WorkCalendar calendar = WorkCalendar.builder().workCalendarId(UUID.randomUUID()).code("CAL").name("Cal")
                .effectiveFrom(LocalDate.of(2026, 1, 1)).status(OrganizationStatus.ACTIVE).build();
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder().workCalendar(calendar)
                .weekday(weekday).shift(shift).build());
        return calendar;
    }

    private Shift shift(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd) {
        Shift shift = Shift.builder().shiftId(UUID.randomUUID()).code("S").name("S")
                .startTime(start).endTime(end).build();
        if (breakStart != null) {
            shift.getBreaks().add(ShiftBreak.builder().shift(shift).startTime(breakStart).endTime(breakEnd).build());
        }
        return shift;
    }
}
