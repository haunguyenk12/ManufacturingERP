package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.service.TimeInterval;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityBoardLineResponse;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityLoadResponse;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityPeriodResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CapacityBoardService tests")
class CapacityBoardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    @Mock WorkOrderOperationRepository workOrderOperationRepository;
    @Mock WorkCalendarLookupService workCalendarLookupService;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock WorkCenterLookupService workCenterLookupService;

    CapacityBoardService service;
    Plant plant;

    @BeforeEach
    void setUp() {
        // The calculator holds the allocation logic under test — a real instance, never a mock (R3).
        CapacityLoadCalculator calculator = new CapacityLoadCalculator(workOrderOperationRepository, workCalendarLookupService);
        service = new CapacityBoardService(workOrderOperationRepository, workCalendarLookupService, calculator,
                organizationLookupService, workCenterLookupService);
        plant = Plant.builder().plantId(UUID.randomUUID()).code("P1").name("Plant 1")
                .timezone(ZONE.getId()).status(OrganizationStatus.ACTIVE).build();
        lenient().when(organizationLookupService.getPlant(plant.getPlantId())).thenReturn(plant);
    }

    @Test
    @DisplayName("computes capacity/load/utilization from the calendar's working windows when the work center has one")
    void getBoard_computesUtilizationAndOverload_whenWorkCenterHasCalendar() {
        WorkCalendar calendar = calendar();
        WorkCenter workCenter = workCenter(calendar, 2);
        // setupMinutes=10 + runMinutesPerUnit=5 * plannedQuantity=10 => 60 min
        WorkOrderOperation operation = operation(workCenter, "10", "5", "10", at(DAY, 8, 0), at(DAY, 9, 0));
        stubBoardPage(DAY, DAY, operation);
        // existing load of 90 min inside 08:00-09:30
        stubLoad(operation(workCenter, "90", "0", "1", at(DAY, 8, 0), at(DAY, 9, 30)));
        // 08:00-16:00 = 480 min/day * capacityUnits(2) = 960 min capacity
        stubCalendar(calendar, List.of(window(DAY, 8, 16)), Set.of());

        CapacityBoardLineResponse line = getSingleLine(DAY, DAY);

        assertThat(line.operationMinutes()).isEqualByComparingTo("60");
        assertThat(line.dayCapacityMinutes()).isEqualByComparingTo("960");
        assertThat(line.dayExistingLoadMinutes()).isEqualByComparingTo("90");
        assertThat(line.utilizationPercent()).isEqualByComparingTo("9.38"); // 90/960*100, HALF_UP
        assertThat(line.overload()).isFalse();
        assertThat(line.calendarExceptionApplies()).isFalse();
    }

    @Test
    @DisplayName("load exceeding capacity is flagged overload")
    void getBoard_loadExceedsCapacity_flagsOverload() {
        WorkCalendar calendar = calendar();
        WorkCenter workCenter = workCenter(calendar, 1);
        WorkOrderOperation operation = operation(workCenter, "0", "0", "10", at(DAY, 8, 0), at(DAY, 9, 0));
        stubBoardPage(DAY, DAY, operation);
        stubLoad(operation(workCenter, "500", "0", "1", at(DAY, 8, 0), at(DAY, 16, 0)));
        // 08:00-16:00 = 480 min * capacityUnits(1) = 480 min capacity < 500 load
        stubCalendar(calendar, List.of(window(DAY, 8, 16)), Set.of());

        CapacityBoardLineResponse line = getSingleLine(DAY, DAY);

        assertThat(line.overload()).isTrue();
    }

    @Test
    @DisplayName("a work center with no calendar reports capacity/utilization as unknown (null), not zero")
    void getBoard_workCenterWithoutCalendar_capacityIsNullNotZero() {
        WorkCenter workCenter = workCenter(null, 1);
        WorkOrderOperation operation = operation(workCenter, "0", "0", "10", at(DAY, 8, 0), at(DAY, 9, 0));
        stubBoardPage(DAY, DAY, operation);
        stubLoad(operation(workCenter, "100", "0", "1", at(DAY, 8, 0), at(DAY, 9, 40)));

        CapacityBoardLineResponse line = getSingleLine(DAY, DAY);

        assertThat(line.dayCapacityMinutes()).isNull();
        assertThat(line.utilizationPercent()).isNull();
        assertThat(line.overload()).isFalse();
        assertThat(line.calendarExceptionApplies()).isFalse();
        // Without a calendar the wall clock decides the share: all 100 minutes fall on DAY.
        assertThat(line.dayExistingLoadMinutes()).isEqualByComparingTo("100");
        verifyNoInteractions(workCalendarLookupService);
    }

    @Test
    @DisplayName("a day marked as a calendar exception is flagged, independent of load/capacity")
    void getBoard_exceptionDay_isFlagged() {
        WorkCalendar calendar = calendar();
        WorkCenter workCenter = workCenter(calendar, 1);
        WorkOrderOperation operation = operation(workCenter, "0", "0", "10", at(DAY, 8, 0), at(DAY, 9, 0));
        stubBoardPage(DAY, DAY, operation);
        stubLoad();
        stubCalendar(calendar, List.of(), Set.of(DAY));

        CapacityBoardLineResponse line = getSingleLine(DAY, DAY);

        assertThat(line.calendarExceptionApplies()).isTrue();
    }

    /**
     * G5 (D9): the C2-8 board dropped a whole operation onto the day it started. One operation of
     * 480 minutes running Monday 12:00 → Tuesday 12:00 over an 08:00–16:00 calendar works 4 h each
     * day, so each day carries 240 — not 480 on Monday and 0 on Tuesday.
     */
    @Test
    @DisplayName("an operation spanning two days is split by the working time it spends in each")
    void getBoard_operationSpanningTwoDays_splitsLoadByWorkingTime() {
        WorkCalendar calendar = calendar();
        WorkCenter workCenter = workCenter(calendar, 1);
        LocalDate tuesday = DAY.plusDays(1);
        WorkOrderOperation operation = operation(workCenter, "480", "0", "1", at(DAY, 12, 0), at(tuesday, 12, 0));
        stubBoardPage(tuesday, tuesday, operation);
        stubLoad(operation);
        stubCalendar(calendar, List.of(window(DAY, 8, 16), window(tuesday, 8, 16)), Set.of());

        CapacityBoardLineResponse line = getSingleLine(tuesday, tuesday);

        // Started on Monday, so before G5 it was invisible on a Tuesday-only board.
        assertThat(line.dayExistingLoadMinutes()).isEqualByComparingTo("240");
        assertThat(line.inPeriodMinutes()).isEqualByComparingTo("240");
        assertThat(line.dayCapacityMinutes()).isEqualByComparingTo("480");
        assertThat(line.utilizationPercent()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("period loads: available = working minutes x units, base = the in-period share, overload and status")
    void getPeriodLoads_reportsTheShareOfEachOperationThatFallsInThePeriod() {
        WorkCalendar calendar = calendar();
        WorkCenter workCenter = workCenter(calendar, 2);
        LocalDate tuesday = DAY.plusDays(1);
        // 960 minutes over Mon 12:00 → Tue 12:00: 4 working hours each day ⇒ 480 belong to Monday.
        stubLoad(operation(workCenter, "960", "0", "1", at(DAY, 12, 0), at(tuesday, 12, 0)));
        when(workCenterLookupService.findActiveWorkCenters(plant.getPlantId())).thenReturn(List.of(workCenter));
        when(workCalendarLookupService.computeWorkingIntervals(eq(calendar.getWorkCalendarId()), any(), any(), eq(ZONE)))
                .thenReturn(List.of(window(DAY, 8, 16), window(tuesday, 8, 16)));

        CapacityPeriodResponse period = service.getPeriodLoads(plant.getPlantId(), DAY, DAY, null);

        CapacityLoadResponse load = period.loads().get(0);
        assertThat(load.baseInPeriodMinutes()).isEqualByComparingTo("480");
        assertThat(load.availableMinutes()).isEqualByComparingTo("960"); // Monday's 480 working minutes x 2
        assertThat(load.overloadMinutes()).isEqualByComparingTo("0");
        assertThat(load.utilizationPercent()).isEqualByComparingTo("50.00");
        assertThat(load.status()).isEqualTo("AVAILABLE");
        assertThat(period.timezone()).isEqualTo(ZONE.getId());
    }

    @Test
    @DisplayName("a period longer than 90 days, both ends included, is refused before anything is read")
    void getPeriodLoads_periodLongerThanNinetyDays_isRefused() {
        assertThatThrownBy(() -> service.getPeriodLoads(plant.getPlantId(), DAY, DAY.plusDays(90), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.INVALID_INPUT));
        verifyNoInteractions(workOrderOperationRepository, workCalendarLookupService);
    }

    @Test
    @DisplayName("exactly 90 days, both ends included, is accepted")
    void getPeriodLoads_ninetyDaysInclusive_isAccepted() {
        stubLoad();
        when(workCenterLookupService.findActiveWorkCenters(plant.getPlantId())).thenReturn(List.of());

        CapacityPeriodResponse period = service.getPeriodLoads(plant.getPlantId(), DAY, DAY.plusDays(89), null);

        assertThat(period.loads()).isEmpty();
    }

    /**
     * P6: a CLOSED work order's schedule still represents real historical load, the same reasoning
     * that keeps COMPLETED in this list — closing must not silently rewrite a day's already-reported
     * utilization. Asserted against the literal enum values, not {@code CapacityBoardService
     * .LOAD_STATUSES} itself — {@link #stubLoad} references that same constant, so comparing the
     * constant to itself would be a tautology (rule R6/D7b.3).
     */
    @Test
    @DisplayName("existing load is computed across RELEASED/IN_PROGRESS/COMPLETED/CLOSED, and CANCELLED is excluded")
    void loadStatuses_includesClosedAndCompletedExcludesCancelled() {
        assertThat(CapacityBoardService.LOAD_STATUSES)
                .containsExactlyInAnyOrder(
                        WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS,
                        WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED)
                .doesNotContain(
                        WorkOrderStatus.CANCELLED, WorkOrderStatus.DRAFT,
                        WorkOrderStatus.PLANNED, WorkOrderStatus.BLOCKED);
    }

    @Test
    @DisplayName("from after to is rejected before the repository is ever queried")
    void getBoard_fromAfterTo_throwsBeforeQuerying() {
        assertThatThrownBy(() -> service.getBoard(
                plant.getPlantId(), DAY, DAY.minusDays(1), null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.INVALID_INPUT));

        verifyNoInteractions(workOrderOperationRepository, workCalendarLookupService);
    }

    private CapacityBoardLineResponse getSingleLine(LocalDate from, LocalDate to) {
        PageResult<CapacityBoardLineResponse> result = service.getBoard(
                plant.getPlantId(), from, to, null, null, PageRequest.of(0, 20));
        assertThat(result.content()).hasSize(1);
        return result.content().get(0);
    }

    private void stubBoardPage(LocalDate from, LocalDate to, WorkOrderOperation operation) {
        Page<WorkOrderOperation> page = new PageImpl<>(List.of(operation), PageRequest.of(0, 20), 1);
        when(workOrderOperationRepository.searchCapacityBoard(
                eq(plant.getPlantId()), isNull(), isNull(),
                eq(from.atStartOfDay(ZONE).toInstant()), eq(to.plusDays(1).atStartOfDay(ZONE).toInstant()), any()))
                .thenReturn(page);
    }

    private void stubLoad(WorkOrderOperation... operations) {
        when(workOrderOperationRepository.findScheduledOverlapping(
                eq(plant.getPlantId()), any(), any(), eq(CapacityBoardService.LOAD_STATUSES)))
                .thenReturn(new ArrayList<>(List.of(operations)));
    }

    private void stubCalendar(WorkCalendar calendar, List<TimeInterval> windows, Set<LocalDate> exceptionDates) {
        when(workCalendarLookupService.computeWorkingIntervals(eq(calendar.getWorkCalendarId()), any(), any(), eq(ZONE)))
                .thenReturn(windows);
        when(workCalendarLookupService.findNonWorkingExceptionDates(eq(calendar.getWorkCalendarId()), any(), any()))
                .thenReturn(exceptionDates);
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return ZonedDateTime.of(day, LocalTime.of(hour, minute), ZONE).toInstant();
    }

    private static TimeInterval window(LocalDate day, int fromHour, int toHour) {
        return new TimeInterval(at(day, fromHour, 0), at(day, toHour, 0));
    }

    private WorkCalendar calendar() {
        return WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(plant)
                .code("CAL").name("Calendar").effectiveFrom(LocalDate.of(2026, 1, 1))
                .status(OrganizationStatus.ACTIVE).build();
    }

    private WorkCenter workCenter(WorkCalendar calendar, int capacityUnits) {
        return WorkCenter.builder().workCenterId(UUID.randomUUID()).plant(plant).code("WC-1").name("Work Center 1")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(capacityUnits)
                .status(OrganizationStatus.ACTIVE).workCalendar(calendar).build();
    }

    private WorkOrderOperation operation(WorkCenter workCenter, String setup, String run, String plannedQuantity,
                                         Instant start, Instant end) {
        WorkOrder workOrder = WorkOrder.builder().workOrderId(UUID.randomUUID()).plant(plant).workOrderNo("WO-1")
                .plannedQuantity(new BigDecimal(plannedQuantity)).status(WorkOrderStatus.RELEASED).build();
        WorkOrderOperation operation = WorkOrderOperation.builder()
                .workOrderOperationId(UUID.randomUUID())
                .workOrder(workOrder)
                .sequence(1)
                .name("Assembly")
                .workCenterCode(workCenter.getCode())
                .workCenter(workCenter)
                .setupMinutes(new BigDecimal(setup))
                .runMinutesPerUnit(new BigDecimal(run))
                .plannedStartAt(start)
                .plannedEndAt(end)
                .build();
        operation.setVersion(0L);
        return operation;
    }
}
