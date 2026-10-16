package com.erp.manufacturing.module.shift.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.WorkCalendarWeeklyShift;
import com.erp.manufacturing.module.shift.domain.Weekday;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.repository.WorkCenterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G5 JPQL of the shift / calendar / work center masters against real Postgres (rule R7): the new
 * {@code search} parameter with a {@code null} value (the {@code lower(bytea)} trap of §0.24 — a mock
 * cannot see it), and the reference checks that refuse a deactivation.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ShiftCalendarReferenceIT.AuditingConfig.class)
class ShiftCalendarReferenceIT extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }
    }

    @Autowired ShiftRepository shiftRepository;
    @Autowired WorkCalendarRepository workCalendarRepository;
    @Autowired WorkCenterRepository workCenterRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;

    @Test
    void search_nullAndTextSearch_onAllThreeMasters() {
        Fixture f = fixture();

        assertThat(shiftRepository.search(f.plant.getPlantId(), null, null, PageRequest.of(0, 10)).getContent())
                .extracting(Shift::getShiftId).containsExactly(f.shift.getShiftId());
        assertThat(shiftRepository.search(f.plant.getPlantId(), null, "day", PageRequest.of(0, 10)).getContent())
                .hasSize(1);
        assertThat(shiftRepository.search(f.plant.getPlantId(), null, "night", PageRequest.of(0, 10)).getContent())
                .isEmpty();
        assertThat(workCalendarRepository.search(f.plant.getPlantId(), null, null, PageRequest.of(0, 10)).getContent())
                .hasSize(1);
        assertThat(workCalendarRepository.search(f.plant.getPlantId(), null, f.tag.toLowerCase(),
                PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(workCenterRepository.search(f.plant.getPlantId(), null, null, PageRequest.of(0, 10)).getContent())
                .hasSize(1);
        assertThat(workCenterRepository.search(f.plant.getPlantId(), null, "assembly", PageRequest.of(0, 10))
                .getContent()).hasSize(1);
    }

    @Test
    void references_countOnlyActiveHolders() {
        Fixture f = fixture();

        assertThat(workCalendarRepository.existsActiveCalendarUsingShift(f.shift.getShiftId())).isTrue();
        assertThat(workCenterRepository.existsByWorkCalendarWorkCalendarIdAndStatus(
                f.calendar.getWorkCalendarId(), OrganizationStatus.ACTIVE)).isTrue();

        f.calendar.deactivate();
        workCalendarRepository.saveAndFlush(f.calendar);
        f.workCenter.deactivate();
        workCenterRepository.saveAndFlush(f.workCenter);

        assertThat(workCalendarRepository.existsActiveCalendarUsingShift(f.shift.getShiftId())).isFalse();
        assertThat(workCenterRepository.existsByWorkCalendarWorkCalendarIdAndStatus(
                f.calendar.getWorkCalendarId(), OrganizationStatus.ACTIVE)).isFalse();
    }

    private record Fixture(String tag, Plant plant, Shift shift, WorkCalendar calendar, WorkCenter workCenter) {}

    private Fixture fixture() {
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Company company = companyRepository.save(Company.builder().code("G5S-" + tag).name("G5 " + tag)
                .status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("PL-" + tag).name("Plant")
                .timezone("Asia/Ho_Chi_Minh").status(OrganizationStatus.ACTIVE).build());
        Shift shift = shiftRepository.save(Shift.builder().plant(plant).code("SH-" + tag).name("Day shift")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).build());
        WorkCalendar calendar = WorkCalendar.builder().plant(plant).code("CAL-" + tag).name("Calendar " + tag)
                .effectiveFrom(LocalDate.of(2026, 1, 1)).build();
        calendar.getWeeklyShifts().add(WorkCalendarWeeklyShift.builder().workCalendar(calendar)
                .weekday(Weekday.MONDAY).shift(shift).build());
        calendar = workCalendarRepository.saveAndFlush(calendar);
        WorkCenter workCenter = workCenterRepository.saveAndFlush(WorkCenter.builder().plant(plant).code("WC-" + tag)
                .name("Assembly line").capacityUnitType(CapacityUnitType.LINE).capacityUnits(1)
                .workCalendar(calendar).build());
        return new Fixture(tag, plant, shift, calendar, workCenter);
    }
}
