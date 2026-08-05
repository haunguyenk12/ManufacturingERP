package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.shift.service.WorkingInterval;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityBoardLineResponse;
import com.erp.manufacturing.module.workorder.repository.CapacityLoadProjection;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CapacityBoardService tests")
class CapacityBoardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    @Mock WorkOrderOperationRepository workOrderOperationRepository;
    @Mock WorkCalendarLookupService workCalendarLookupService;

    CapacityBoardService service;

    @BeforeEach
    void setUp() {
        service = new CapacityBoardService(workOrderOperationRepository, workCalendarLookupService);
    }

    @Test
    @DisplayName("computes capacity/load/utilization from the calendar's working windows when the work center has one")
    void getBoard_computesUtilizationAndOverload_whenWorkCenterHasCalendar() {
        Plant plant = plant();
        WorkCalendar calendar = calendar(plant);
        WorkCenter workCenter = workCenter(plant, calendar, 2);
        // setupMinutes=10 + runMinutesPerUnit=5 * plannedQuantity=10 => 60 min
        WorkOrderOperation operation = operation(workOrder(plant, new BigDecimal("10")), workCenter, "10", "5");
        stubBoardPage(plant, operation);
        stubLoad(plant, workCenter.getWorkCenterId(), new BigDecimal("90"));
        // 08:00-16:00 = 480 min/day * capacityUnits(2) = 960 min capacity
        stubCalendar(calendar, Map.of(DAY, List.of(
                new WorkingInterval(DAY.atTime(8, 0), DAY.atTime(16, 0)))), Set.of());

        CapacityBoardLineResponse line = getSingleLine(plant, null, null);

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
        Plant plant = plant();
        WorkCalendar calendar = calendar(plant);
        WorkCenter workCenter = workCenter(plant, calendar, 1);
        WorkOrderOperation operation = operation(workOrder(plant, new BigDecimal("10")), workCenter, "0", "0");
        stubBoardPage(plant, operation);
        stubLoad(plant, workCenter.getWorkCenterId(), new BigDecimal("500"));
        // 08:00-16:00 = 480 min * capacityUnits(1) = 480 min capacity < 500 load
        stubCalendar(calendar, Map.of(DAY, List.of(
                new WorkingInterval(DAY.atTime(8, 0), DAY.atTime(16, 0)))), Set.of());

        CapacityBoardLineResponse line = getSingleLine(plant, null, null);

        assertThat(line.overload()).isTrue();
    }

    @Test
    @DisplayName("a work center with no calendar reports capacity/utilization as unknown (null), not zero")
    void getBoard_workCenterWithoutCalendar_capacityIsNullNotZero() {
        Plant plant = plant();
        WorkCenter workCenter = workCenter(plant, null, 1);
        WorkOrderOperation operation = operation(workOrder(plant, new BigDecimal("10")), workCenter, "0", "0");
        stubBoardPage(plant, operation);
        stubLoad(plant, workCenter.getWorkCenterId(), new BigDecimal("100"));

        CapacityBoardLineResponse line = getSingleLine(plant, null, null);

        assertThat(line.dayCapacityMinutes()).isNull();
        assertThat(line.utilizationPercent()).isNull();
        assertThat(line.overload()).isFalse();
        assertThat(line.calendarExceptionApplies()).isFalse();
        verifyNoInteractions(workCalendarLookupService);
    }

    @Test
    @DisplayName("a day marked as a calendar exception is flagged, independent of load/capacity")
    void getBoard_exceptionDay_isFlagged() {
        Plant plant = plant();
        WorkCalendar calendar = calendar(plant);
        WorkCenter workCenter = workCenter(plant, calendar, 1);
        WorkOrderOperation operation = operation(workOrder(plant, new BigDecimal("10")), workCenter, "0", "0");
        stubBoardPage(plant, operation);
        stubLoad(plant, workCenter.getWorkCenterId(), BigDecimal.ZERO);
        stubCalendar(calendar, Map.of(DAY, List.of()), Set.of(DAY));

        CapacityBoardLineResponse line = getSingleLine(plant, null, null);

        assertThat(line.calendarExceptionApplies()).isTrue();
    }

    @Test
    @DisplayName("from after to is rejected before the repository is ever queried")
    void getBoard_fromAfterTo_throwsBeforeQuerying() {
        Plant plant = plant();

        assertThatThrownBy(() -> service.getBoard(
                plant.getPlantId(), DAY, DAY.minusDays(1), null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.INVALID_INPUT));

        verifyNoInteractions(workOrderOperationRepository, workCalendarLookupService);
    }

    private CapacityBoardLineResponse getSingleLine(Plant plant, UUID workCenterId, WorkOrderStatus status) {
        PageResult<CapacityBoardLineResponse> result = service.getBoard(
                plant.getPlantId(), DAY, DAY, workCenterId, status, PageRequest.of(0, 20));
        assertThat(result.content()).hasSize(1);
        return result.content().get(0);
    }

    private void stubBoardPage(Plant plant, WorkOrderOperation operation) {
        Page<WorkOrderOperation> page = new PageImpl<>(List.of(operation), PageRequest.of(0, 20), 1);
        when(workOrderOperationRepository.searchCapacityBoard(
                eq(plant.getPlantId()), isNull(), isNull(), eq(DAY), eq(DAY), any()))
                .thenReturn(page);
    }

    private void stubLoad(Plant plant, UUID workCenterId, BigDecimal loadMinutes) {
        CapacityLoadProjection projection = mock(CapacityLoadProjection.class);
        when(projection.getWorkCenterId()).thenReturn(workCenterId);
        when(projection.getDay()).thenReturn(DAY);
        when(projection.getLoadMinutes()).thenReturn(loadMinutes);
        when(workOrderOperationRepository.aggregateExistingLoad(
                eq(plant.getPlantId()), eq(DAY), eq(DAY), eq(CapacityBoardService.LOAD_STATUSES)))
                .thenReturn(List.of(projection));
    }

    private void stubCalendar(WorkCalendar calendar, Map<LocalDate, List<WorkingInterval>> windows,
                               Set<LocalDate> exceptionDates) {
        when(workCalendarLookupService.computeWorkingWindows(eq(calendar.getWorkCalendarId()), eq(DAY), eq(DAY)))
                .thenReturn(windows);
        when(workCalendarLookupService.findNonWorkingExceptionDates(eq(calendar.getWorkCalendarId()), eq(DAY), eq(DAY)))
                .thenReturn(exceptionDates);
    }

    private Plant plant() {
        return Plant.builder().plantId(UUID.randomUUID()).code("P1").name("Plant 1")
                .timezone(ZONE.getId()).status(OrganizationStatus.ACTIVE).build();
    }

    private WorkCalendar calendar(Plant plant) {
        return WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(plant)
                .code("CAL").name("Calendar").effectiveFrom(LocalDate.of(2026, 1, 1))
                .status(OrganizationStatus.ACTIVE).build();
    }

    private WorkCenter workCenter(Plant plant, WorkCalendar calendar, int capacityUnits) {
        return WorkCenter.builder().workCenterId(UUID.randomUUID()).plant(plant).code("WC-1").name("Work Center 1")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(capacityUnits)
                .status(OrganizationStatus.ACTIVE).workCalendar(calendar).build();
    }

    private WorkOrder workOrder(Plant plant, BigDecimal plannedQuantity) {
        return WorkOrder.builder().workOrderId(UUID.randomUUID()).plant(plant).workOrderNo("WO-1")
                .plannedQuantity(plannedQuantity).status(WorkOrderStatus.RELEASED).build();
    }

    private WorkOrderOperation operation(WorkOrder workOrder, WorkCenter workCenter, String setup, String run) {
        ZonedDateTime start = ZonedDateTime.of(DAY, java.time.LocalTime.of(8, 0), ZONE);
        WorkOrderOperation operation = WorkOrderOperation.builder()
                .workOrderOperationId(UUID.randomUUID())
                .workOrder(workOrder)
                .sequence(1)
                .name("Assembly")
                .workCenterCode(workCenter.getCode())
                .workCenter(workCenter)
                .setupMinutes(new BigDecimal(setup))
                .runMinutesPerUnit(new BigDecimal(run))
                .plannedStartAt(start.toInstant())
                .plannedEndAt(start.plusHours(1).toInstant())
                .build();
        operation.setVersion(0L);
        return operation;
    }
}
