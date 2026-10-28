package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.service.TimeInterval;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CF-5 (B269): release schedules with the work center's {@code capacityUnits}, through the same walk as the plant
 * capacity preview. FE fixtures: 08:00–12:00 and 13:00–17:00, 10 units × 12 min = 120 capacity minutes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderScheduler")
class WorkOrderSchedulerTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Mock WorkCalendarLookupService workCalendarLookupService;

    @Test
    void twoUnits_120CapacityMinutesFrom0800_endAt0900_andTheSlotCarriesWhatItUsed() {
        Fixture f = fixture(2);
        stubDay(f.calendar);

        WorkOrderScheduler.Slot slot = schedule(f, at("08:00")).slots().get(0);

        assertThat(slot.start()).isEqualTo(at("08:00"));
        assertThat(slot.end()).isEqualTo(at("09:00"));
        assertThat(slot.onCalendar()).isTrue();
        assertThat(slot.capacityUnits()).isEqualTo(2);
        assertThat(slot.requiredMinutes()).isEqualByComparingTo("120");
        assertThat(slot.activeElapsedMinutes()).isEqualByComparingTo("60");
        assertThat(slot.workMinutes()).isEqualTo(120L);
    }

    @Test
    void oneUnit_120CapacityMinutesFrom0800_endAt1000() {
        Fixture f = fixture(1);
        stubDay(f.calendar);

        WorkOrderScheduler.Slot slot = schedule(f, at("08:00")).slots().get(0);

        assertThat(slot.end()).isEqualTo(at("10:00"));
        assertThat(slot.activeElapsedMinutes()).isEqualByComparingTo("120");
    }

    @Test
    void startAt1130_twoUnits_crossesLunch_endAt1330() {
        Fixture f = fixture(2);
        stubDay(f.calendar);

        WorkOrderScheduler.Slot slot = schedule(f, at("11:30")).slots().get(0);

        assertThat(slot.start()).isEqualTo(at("11:30"));
        assertThat(slot.end()).isEqualTo(at("13:30"));
    }

    /** A start in non-working time is moved to the first working moment, like the plant preview does. */
    @Test
    void startBeforeTheShift_snapsToTheFirstWorkingMoment() {
        Fixture f = fixture(2);
        stubDay(f.calendar);

        WorkOrderScheduler.Slot slot = schedule(f, at("06:00")).slots().get(0);

        assertThat(slot.start()).isEqualTo(at("08:00"));
        assertThat(slot.end()).isEqualTo(at("09:00"));
    }

    @Test
    void secondOperation_startsWhereTheFirstEnded() {
        Fixture f = fixture(2);
        f.workOrder.getOperations().add(operation(f.workOrder, 2, f.workCenter, "0", "6"));
        stubDay(f.calendar);

        WorkOrderScheduler.Schedule schedule = schedule(f, at("08:00"));

        // second: 6 × 10 = 60 capacity minutes on 2 units = 30 working minutes
        assertThat(schedule.slots().get(1).start()).isEqualTo(at("09:00"));
        assertThat(schedule.slots().get(1).end()).isEqualTo(at("09:30"));
    }

    @Test
    void noWorkCenter_isOneUnitInContinuousTime_andTheCalendarModuleIsNeverAsked() {
        WorkOrder workOrder = workOrder();
        WorkOrderOperation operation = operation(workOrder, 1, null, "0", "12");
        workOrder.getOperations().add(operation);

        WorkOrderScheduler.Schedule schedule = new WorkOrderScheduler(workCalendarLookupService)
                .compute(workOrder, at("11:30"), BigDecimal.TEN);

        WorkOrderScheduler.Slot slot = schedule.slots().get(0);
        assertThat(slot.capacityUnits()).isEqualTo(1);
        assertThat(slot.onCalendar()).isFalse();
        assertThat(slot.start()).isEqualTo(at("11:30"));
        assertThat(slot.end()).isEqualTo(at("13:30")); // continuous, no lunch skipped
        assertThat(schedule.calendarRevision()).isNull();
        verifyNoInteractions(workCalendarLookupService);
    }

    /** No calendar: continuous time, still divided — 25 capacity minutes on 2 units end after 750 s. */
    @Test
    void workCenterWithoutCalendar_dividesInContinuousTime_ceilSeconds() {
        WorkOrder workOrder = workOrder();
        WorkCenter workCenter = workCenter(workOrder.getPlant(), null, 2);
        workOrder.getOperations().add(operation(workOrder, 1, workCenter, "25", "0"));

        WorkOrderScheduler.Slot slot = new WorkOrderScheduler(workCalendarLookupService)
                .compute(workOrder, at("08:00"), BigDecimal.TEN).slots().get(0);

        assertThat(slot.end()).isEqualTo(at("08:00").plusSeconds(750));
        assertThat(slot.capacityUnits()).isEqualTo(2);
        assertThat(slot.activeElapsedMinutes()).isEqualByComparingTo("12.5");
        verifyNoInteractions(workCalendarLookupService);
    }

    /** The first 31-day chunk has no working time; the walk asks for the next one rather than giving up. */
    @Test
    void calendarWithoutTimeInTheFirstChunk_fetchesTheNextChunk() {
        Fixture f = fixture(2);
        Instant start = at("08:00");
        Instant secondChunk = start.plus(WorkOrderScheduler.WINDOW_CHUNK_DAYS, ChronoUnit.DAYS);
        UUID calendarId = f.calendar.getWorkCalendarId();
        when(workCalendarLookupService.computeWorkingIntervals(eq(calendarId), eq(start), any(), eq(UTC)))
                .thenReturn(List.of());
        when(workCalendarLookupService.computeWorkingIntervals(eq(calendarId), eq(secondChunk), any(), eq(UTC)))
                .thenReturn(List.of(new TimeInterval(secondChunk.plus(1, ChronoUnit.HOURS),
                        secondChunk.plus(9, ChronoUnit.HOURS))));

        WorkOrderScheduler.Slot slot = schedule(f, start).slots().get(0);

        assertThat(slot.start()).isEqualTo(secondChunk.plus(1, ChronoUnit.HOURS));
        assertThat(slot.end()).isEqualTo(secondChunk.plus(2, ChronoUnit.HOURS));
    }

    @Test
    void calendarWithoutAnyWorkingTimeWithinTheHorizon_isRefusedNamingTheRule() {
        Fixture f = fixture(2);
        when(workCalendarLookupService.computeWorkingIntervals(any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> schedule(f, at("08:00")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
        verify(workCalendarLookupService, atLeast(WorkOrderScheduler.MAX_HORIZON_DAYS / WorkOrderScheduler.WINDOW_CHUNK_DAYS))
                .computeWorkingIntervals(any(), any(), any(), any());
    }

    /** Release and a manual adjustment that leaves the end out walk the same way. */
    @Test
    void computeEnd_equalsTheSlotEndForTheSameInputs() {
        Fixture f = fixture(2);
        stubDay(f.calendar);
        WorkOrderScheduler scheduler = new WorkOrderScheduler(workCalendarLookupService);

        Instant slotEnd = scheduler.compute(f.workOrder, at("11:30"), BigDecimal.TEN).slots().get(0).end();
        Instant computed = scheduler.computeEnd(f.operation, at("11:30"), BigDecimal.TEN, 2);

        assertThat(computed).isEqualTo(slotEnd).isEqualTo(at("13:30"));
        assertThat(scheduler.computeEnd(f.operation, at("08:00"), BigDecimal.TEN, 1)).isEqualTo(at("10:00"));
    }

    // ---------------------------------------------------------------- helpers

    private record Fixture(WorkOrder workOrder, WorkCalendar calendar, WorkCenter workCenter,
                           WorkOrderOperation operation) {}

    private Fixture fixture(int capacityUnits) {
        WorkOrder workOrder = workOrder();
        WorkCalendar calendar = WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(workOrder.getPlant())
                .code("CAL").name("Cal").effectiveFrom(LocalDate.of(2026, 1, 1)).status(OrganizationStatus.ACTIVE).build();
        WorkCenter workCenter = workCenter(workOrder.getPlant(), calendar, capacityUnits);
        WorkOrderOperation operation = operation(workOrder, 1, workCenter, "0", "12");
        workOrder.getOperations().add(operation);
        return new Fixture(workOrder, calendar, workCenter, operation);
    }

    private WorkOrderScheduler.Schedule schedule(Fixture f, Instant start) {
        return new WorkOrderScheduler(workCalendarLookupService).compute(f.workOrder, start, BigDecimal.TEN);
    }

    private void stubDay(WorkCalendar calendar) {
        when(workCalendarLookupService.computeWorkingIntervals(eq(calendar.getWorkCalendarId()), any(), any(), eq(UTC)))
                .thenReturn(List.of(new TimeInterval(at("08:00"), at("12:00")),
                        new TimeInterval(at("13:00"), at("17:00"))));
    }

    private static WorkOrder workOrder() {
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).code("P1").name("Plant").timezone("UTC")
                .status(OrganizationStatus.ACTIVE).build();
        return WorkOrder.builder().workOrderId(UUID.randomUUID()).plant(plant).workOrderNo("WO-1")
                .plannedQuantity(BigDecimal.TEN).status(WorkOrderStatus.PLANNED).operations(new ArrayList<>()).build();
    }

    private static WorkCenter workCenter(Plant plant, WorkCalendar calendar, int capacityUnits) {
        return WorkCenter.builder().workCenterId(UUID.randomUUID()).plant(plant).code("WC-1").name("Cell")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(capacityUnits)
                .status(OrganizationStatus.ACTIVE).workCalendar(calendar).build();
    }

    private static WorkOrderOperation operation(WorkOrder workOrder, int sequence, WorkCenter workCenter,
                                                String setup, String run) {
        return WorkOrderOperation.builder().workOrderOperationId(UUID.randomUUID()).workOrder(workOrder)
                .sequence(sequence).name("Op " + sequence)
                .workCenterCode(workCenter == null ? "WC-LEGACY" : workCenter.getCode()).workCenter(workCenter)
                .setupMinutes(new BigDecimal(setup)).runMinutesPerUnit(new BigDecimal(run)).build();
    }

    private static Instant at(String time) {
        return Instant.parse("2026-10-13T" + time + ":00Z");
    }
}
