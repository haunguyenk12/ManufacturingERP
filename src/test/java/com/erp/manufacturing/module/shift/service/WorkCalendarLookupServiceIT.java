package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.ShiftBreak;
import com.erp.manufacturing.module.shift.domain.Weekday;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.WorkCalendarException;
import com.erp.manufacturing.module.shift.domain.WorkCalendarWeeklyShift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WorkCalendarLookupService} against a real Postgres, with a calendar shaped the way a real
 * factory calendar is shaped: shifts that have breaks.
 *
 * <p>Rule {@code R7}: the defect this guards is invisible to a mocked repository. It lived entirely
 * in the {@code @EntityGraph} on {@code WorkCalendarRepository}, which listed
 * {@code weeklyShifts.shift.breaks} alongside {@code weeklyShifts} — two bag collections in one
 * query, so Hibernate threw {@code MultipleBagFetchException} and every
 * {@code POST /work-orders/{id}/release} whose work centre carried such a calendar answered
 * <b>500</b>. Unit tests mock this repository away and never build the query; the existing
 * {@code *IT} classes did have shifts, but none with breaks, so the second bag was never reached.
 *
 * <p>Discovered while seeding the demo dataset ({@code scripts/demo/}) — the first data in the repo
 * to combine a shift with breaks, a calendar referencing it, and a released work order. Third time
 * this trap has bitten: C2-4 ({@code operations} + {@code componentLines}, CLAUDE.md §0.27) and C2-7
 * ({@code weeklyShifts} + {@code exceptions}, §0.29) were the first two.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({WorkCalendarLookupService.class, WorkCalendarLookupServiceIT.AuditingConfig.class})
class WorkCalendarLookupServiceIT extends AbstractPostgresIntegrationTest {

    /** {@code @DataJpaTest} does not pick up {@code JpaAuditingConfig}; the NOT NULL audit columns
     *  still have to be filled the way production fills them. */
    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }
    }

    private static final ZoneId ZONE = ZoneId.of("UTC");
    /** 2026-08-17 is a Monday — the weekday the calendar below assigns its shift to. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 17);

    @Autowired
    WorkCalendarLookupService lookupService;

    @Autowired
    TestEntityManager em;

    private UUID calendarId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Company company = em.persist(Company.builder()
                .code("CAL-CO-" + suffix).name("Calendar fetch fixture").build());
        Plant plant = em.persist(Plant.builder()
                .company(company).code("CAL-P-" + suffix).name("Calendar fetch plant")
                .timezone("UTC").build());

        // 06:00-14:00 = 480 minutes, minus a 30-minute break = 450 net working minutes.
        Shift shift = em.persist(Shift.builder()
                .plant(plant).code("CAL-SH-" + suffix).name("Day shift with a break")
                .startTime(LocalTime.of(6, 0)).endTime(LocalTime.of(14, 0)).build());
        shift.getBreaks().add(em.persist(ShiftBreak.builder()
                .shift(shift).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(9, 30)).build()));

        WorkCalendar calendar = em.persist(WorkCalendar.builder()
                .plant(plant).code("CAL-" + suffix).name("Mon-Tue, one shift")
                .effectiveFrom(MONDAY.minusDays(30)).build());
        calendar.getWeeklyShifts().add(em.persist(WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar).weekday(Weekday.MONDAY).shift(shift).build()));
        calendar.getWeeklyShifts().add(em.persist(WorkCalendarWeeklyShift.builder()
                .workCalendar(calendar).weekday(Weekday.TUESDAY).shift(shift).build()));
        calendar.getExceptions().add(em.persist(WorkCalendarException.builder()
                .workCalendar(calendar).exceptionDate(MONDAY.plusDays(7)).reason("Nghi le").build()));

        em.flush();
        em.clear();  // force the lookup to go through the entity graph rather than the 1st-level cache
        calendarId = calendar.getWorkCalendarId();
    }

    @Test
    void computeWorkingWindows_calendarWhoseShiftsHaveBreaks_loadsWithoutMultipleBagFetch() {
        var windows = lookupService.computeWorkingWindows(calendarId, MONDAY, MONDAY);

        // Before the fix this line never ran: the query itself threw MultipleBagFetchException.
        assertThat(windows).containsOnlyKeys(MONDAY);
        // The break splits the shift into two windows — proof the breaks collection was really
        // loaded, not merely that the query stopped throwing.
        assertThat(windows.get(MONDAY)).hasSize(2);
    }

    @Test
    void computeEndInstant_subtractsTheBreakFromTheAvailableWorkingTime() {
        Instant sixAm = ZonedDateTime.of(MONDAY, LocalTime.of(6, 0), ZONE).toInstant();

        // 450 net minutes on Monday: 06:00-09:00 (180) + 09:30-14:00 (270). Asking for exactly 450
        // must land on the end of Monday's shift. Had breaks been silently missing, 450 minutes of
        // an unbroken 480-minute window would have ended at 13:30 instead.
        Instant end = lookupService.computeEndInstant(calendarId, sixAm, ZONE, 450);

        assertThat(end).isEqualTo(ZonedDateTime.of(MONDAY, LocalTime.of(14, 0), ZONE).toInstant());
    }

    @Test
    void findNonWorkingExceptionDates_stillSeesTheExceptionsBagAlongsideTheOthers() {
        Set<LocalDate> nonWorking =
                lookupService.findNonWorkingExceptionDates(calendarId, MONDAY, MONDAY.plusDays(14));

        assertThat(nonWorking).containsExactly(MONDAY.plusDays(7));
    }

    @Test
    void computeWorkingWindows_weeklyShiftRowsAreNotDuplicatedByTheFetchJoin() {
        // Two weekday rows point at the SAME shift. A join fetch that multiplied rows would show up
        // here as a duplicated window on a single day.
        var windows = lookupService.computeWorkingWindows(calendarId, MONDAY, MONDAY.plusDays(1));

        assertThat(windows.get(MONDAY)).hasSize(2);
        assertThat(windows.get(MONDAY.plusDays(1))).hasSize(2);
        assertThat(windows.get(MONDAY.plusDays(2))).isNull();
        assertThat(List.copyOf(windows.keySet())).containsExactly(MONDAY, MONDAY.plusDays(1));
    }
}
