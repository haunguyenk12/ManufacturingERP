package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.schedule.WorkOrderSchedulePreviewRequest;
import com.erp.manufacturing.module.workorder.dto.schedule.WorkOrderSchedulePreviewResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G8a: the work order schedule preview computes what release would commit and writes nothing; load other
 * work orders already committed on the same work center is a warning, the work order's own is not.
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderSchedulePreviewServiceTest {

    private static final Instant START = Instant.parse("2026-10-20T01:00:00Z");

    @Mock WorkOrderRepository workOrderRepository;
    @Mock WorkCalendarLookupService workCalendarLookupService;
    @Mock CapacityLoadCalculator calculator;

    WorkOrderSchedulePreviewService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderSchedulePreviewService(workOrderRepository,
                new WorkOrderScheduler(workCalendarLookupService), calculator);
    }

    /** Same formula as release: ceil(setup + run × quantity), operations back to back. */
    @Test
    void preview_placesOperationsBackToBackWithReleaseFormula_andWritesNothing() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.PLANNED);
        WorkCenter workCenter = workCenter(workOrder.getPlant());
        workOrder.getOperations().add(operation(workOrder, 10, workCenter, "5", "1.5"));
        workOrder.getOperations().add(operation(workOrder, 20, workCenter, "0", "2"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(calculator.loadOperations(any(), any(), any())).thenReturn(List.of());

        WorkOrderSchedulePreviewResponse preview = service.preview(workOrder.getWorkOrderId(),
                new WorkOrderSchedulePreviewRequest(0L, START, new BigDecimal("3")));

        // 5 + 1.5 × 3 = 9.5 → 10 minutes; then 2 × 3 = 6 minutes.
        assertThat(preview.rows()).extracting(WorkOrderSchedulePreviewResponse.Row::workMinutes).containsExactly(10L, 6L);
        assertThat(preview.rows().get(0).startsAt()).isEqualTo(START);
        assertThat(preview.rows().get(0).endsAt()).isEqualTo(START.plusSeconds(600));
        assertThat(preview.rows().get(1).startsAt()).isEqualTo(START.plusSeconds(600));
        assertThat(preview.rows().get(1).endsAt()).isEqualTo(START.plusSeconds(960));
        assertThat(preview.warnings()).extracting(WorkOrderSchedulePreviewResponse.Warning::code)
                .containsExactly("NO_CALENDAR", "NO_CALENDAR");
        assertThat(workOrder.getOperations().get(0).getPlannedStartAt()).isNull();
        verify(workOrderRepository, never()).save(any());
        verify(workOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void preview_overlapWithAnotherWorkOrderOnTheSameWorkCenter_isAWarning_ownLoadIsIgnored() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT);
        WorkCenter workCenter = workCenter(workOrder.getPlant());
        workOrder.getOperations().add(operation(workOrder, 10, workCenter, "60", "0"));
        WorkOrder other = workOrder(WorkOrderStatus.RELEASED);
        WorkOrderOperation busy = operation(other, 10, workCenter, "0", "0");
        busy.setPlannedStartAt(START.plusSeconds(1800));
        busy.setPlannedEndAt(START.plusSeconds(5400));
        WorkOrderOperation own = operation(workOrder, 99, workCenter, "0", "0");
        own.setPlannedStartAt(START);
        own.setPlannedEndAt(START.plusSeconds(3600));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(calculator.loadOperations(eq(workOrder.getPlant().getPlantId()), eq(START), eq(START.plusSeconds(3600))))
                .thenReturn(List.of(busy, own));

        WorkOrderSchedulePreviewResponse preview = service.preview(workOrder.getWorkOrderId(),
                new WorkOrderSchedulePreviewRequest(null, START, null));

        assertThat(preview.rows().get(0).overlappingOperations()).isEqualTo(1);
        assertThat(preview.warnings()).extracting(WorkOrderSchedulePreviewResponse.Warning::code).contains("OVERLAP");
    }

    @Test
    void preview_ofAReleasedWorkOrder_isAStateConflict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.preview(workOrder.getWorkOrderId(), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    @Test
    void preview_staleExpectedVersion_isAConcurrentModification() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.preview(workOrder.getWorkOrderId(),
                new WorkOrderSchedulePreviewRequest(3L, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
    }

    private WorkOrder workOrder(WorkOrderStatus status) {
        Company company = Company.builder().companyId(UUID.randomUUID()).code("CO").name("Co")
                .status(OrganizationStatus.ACTIVE).build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P1").name("Plant")
                .status(OrganizationStatus.ACTIVE).build();
        WorkOrder workOrder = WorkOrder.builder().workOrderId(UUID.randomUUID()).company(company).plant(plant)
                .workOrderNo("WO-1").plannedQuantity(BigDecimal.TEN).status(status)
                .operations(new ArrayList<>()).build();
        workOrder.setVersion(0L);
        return workOrder;
    }

    private WorkCenter workCenter(Plant plant) {
        return WorkCenter.builder().workCenterId(UUID.randomUUID()).plant(plant).code("WC-1").name("Cell")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1).status(OrganizationStatus.ACTIVE).build();
    }

    private WorkOrderOperation operation(WorkOrder workOrder, int sequence, WorkCenter workCenter,
                                         String setup, String run) {
        return WorkOrderOperation.builder().workOrderOperationId(UUID.randomUUID()).workOrder(workOrder)
                .sequence(sequence).name("Op " + sequence).workCenterCode(workCenter.getCode()).workCenter(workCenter)
                .setupMinutes(new BigDecimal(setup)).runMinutesPerUnit(new BigDecimal(run)).build();
    }
}
