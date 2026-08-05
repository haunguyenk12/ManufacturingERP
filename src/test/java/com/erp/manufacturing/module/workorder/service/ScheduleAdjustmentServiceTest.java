package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentRequest;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.service.query.CapacityBoardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleAdjustmentService tests")
class ScheduleAdjustmentServiceTest {

    private static final Instant HOUR_0 = Instant.parse("2026-01-05T08:00:00Z");
    private static final Instant HOUR_1 = Instant.parse("2026-01-05T09:00:00Z");
    private static final Instant HOUR_2 = Instant.parse("2026-01-05T10:00:00Z");
    private static final Instant HOUR_3 = Instant.parse("2026-01-05T11:00:00Z");

    private static final CapacityBoardService.DayContext EMPTY_CONTEXT =
            new CapacityBoardService.DayContext(Map.of(), Map.of(), Map.of());

    @Mock WorkOrderOperationRepository workOrderOperationRepository;
    @Mock CapacityBoardService capacityBoardService;

    ScheduleAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleAdjustmentService(workOrderOperationRepository, capacityBoardService);
    }

    @Test
    void adjust_missingOperation_throwsResourceNotFound() {
        UUID workOrderId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        when(workOrderOperationRepository.findByWorkOrderOperationIdAndWorkOrderWorkOrderId(operationId, workOrderId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adjust(workOrderId, operationId, request(HOUR_0, HOUR_1, 0L)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void adjust_operationWithoutWorkCenter_throwsOperationNotAllowedBeforeAnyWrite() {
        WorkOrder workOrder = workOrder();
        WorkOrderOperation operation = operation(workOrder, null, 1, HOUR_0, HOUR_1);
        stubFind(workOrder, operation);

        assertThatThrownBy(() -> service.adjust(
                workOrder.getWorkOrderId(), operation.getWorkOrderOperationId(), request(HOUR_1, HOUR_2, 0L)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
        verify(workOrderOperationRepository, never()).save(any());
    }

    @Test
    void adjust_neverScheduled_throwsOperationNotAllowedBeforeAnyWrite() {
        WorkOrder workOrder = workOrder();
        WorkCenter workCenter = workCenter();
        WorkOrderOperation operation = operation(workOrder, workCenter, 1, null, null);
        stubFind(workOrder, operation);

        assertThatThrownBy(() -> service.adjust(
                workOrder.getWorkOrderId(), operation.getWorkOrderOperationId(), request(HOUR_1, HOUR_2, 0L)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
        verify(workOrderOperationRepository, never()).save(any());
    }

    @Test
    void adjust_staleExpectedVersion_throwsConcurrentModificationBeforeAnyWrite() {
        WorkOrder workOrder = workOrder();
        WorkCenter workCenter = workCenter();
        WorkOrderOperation operation = operation(workOrder, workCenter, 1, HOUR_0, HOUR_1);
        operation.setVersion(2L);
        stubFind(workOrder, operation);

        assertThatThrownBy(() -> service.adjust(
                workOrder.getWorkOrderId(), operation.getWorkOrderOperationId(), request(HOUR_1, HOUR_2, 1L)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        verify(workOrderOperationRepository, never()).save(any());
    }

    @Test
    void adjust_endNotAfterStart_throwsInvalidInputBeforeAnyWrite() {
        WorkOrder workOrder = workOrder();
        WorkCenter workCenter = workCenter();
        WorkOrderOperation operation = operation(workOrder, workCenter, 1, HOUR_0, HOUR_1);
        stubFind(workOrder, operation);

        assertThatThrownBy(() -> service.adjust(
                workOrder.getWorkOrderId(), operation.getWorkOrderOperationId(), request(HOUR_2, HOUR_1, 0L)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.INVALID_INPUT));
        verify(workOrderOperationRepository, never()).save(any());
    }

    /**
     * Decision #3 (NEXT_PHASE_PLAN.md): a window that respects both siblings' schedules reports
     * {@code sequenceConflict = false} and the write still happens.
     */
    @Test
    void adjust_cleanWindow_persistsAndReportsNoSequenceConflict() {
        WorkOrder workOrder = workOrder();
        WorkCenter workCenter = workCenter();
        WorkOrderOperation predecessor = operation(workOrder, workCenter, 1, HOUR_0, HOUR_1);
        WorkOrderOperation target = operation(workOrder, workCenter, 2, HOUR_1, HOUR_2);
        WorkOrderOperation successor = operation(workOrder, workCenter, 3, HOUR_2, HOUR_3);
        stubFind(workOrder, target);
        stubSiblings(workOrder, predecessor, target, successor);
        when(capacityBoardService.buildDayContext(any(), any(), any(), any())).thenReturn(EMPTY_CONTEXT);
        when(workOrderOperationRepository.save(target)).thenReturn(target);

        ScheduleAdjustmentResponse response = service.adjust(workOrder.getWorkOrderId(),
                target.getWorkOrderOperationId(), request(HOUR_1, HOUR_1.plusSeconds(1800), 0L));

        assertThat(response.sequenceConflict()).isFalse();
        assertThat(response.plannedStartAt()).isEqualTo(HOUR_1);
        assertThat(response.plannedEndAt()).isEqualTo(HOUR_1.plusSeconds(1800));
        assertThat(response.scheduleAdjustmentReason()).isEqualTo("Reason");
        assertThat(target.getPlannedStartAt()).isEqualTo(HOUR_1);
        verify(workOrderOperationRepository).save(target);
    }

    /**
     * A window that starts before the predecessor's own planned end is still persisted (decision #3
     * — no auto-shift, no hard block) but is reported as a conflict for the manager to see.
     */
    @Test
    void adjust_windowStartingBeforePredecessorEnds_stillPersistsButFlagsSequenceConflict() {
        WorkOrder workOrder = workOrder();
        WorkCenter workCenter = workCenter();
        WorkOrderOperation predecessor = operation(workOrder, workCenter, 1, HOUR_0, HOUR_1);
        WorkOrderOperation target = operation(workOrder, workCenter, 2, HOUR_1, HOUR_2);
        stubFind(workOrder, target);
        stubSiblings(workOrder, predecessor, target);
        when(capacityBoardService.buildDayContext(any(), any(), any(), any())).thenReturn(EMPTY_CONTEXT);
        when(workOrderOperationRepository.save(target)).thenReturn(target);

        Instant newStart = HOUR_1.minusSeconds(1800); // 30 min before predecessor's planned end
        ScheduleAdjustmentResponse response = service.adjust(workOrder.getWorkOrderId(),
                target.getWorkOrderOperationId(), request(newStart, HOUR_2, 0L));

        assertThat(response.sequenceConflict()).isTrue();
        assertThat(target.getPlannedStartAt()).isEqualTo(newStart);
        verify(workOrderOperationRepository).save(target);
    }

    private void stubFind(WorkOrder workOrder, WorkOrderOperation operation) {
        when(workOrderOperationRepository.findByWorkOrderOperationIdAndWorkOrderWorkOrderId(
                operation.getWorkOrderOperationId(), workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(operation));
    }

    private void stubSiblings(WorkOrder workOrder, WorkOrderOperation... siblings) {
        when(workOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(workOrder.getWorkOrderId()))
                .thenReturn(List.of(siblings));
    }

    private ScheduleAdjustmentRequest request(Instant start, Instant end, Long expectedVersion) {
        return new ScheduleAdjustmentRequest(start, end, "Reason", expectedVersion);
    }

    private WorkOrder workOrder() {
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).code("P1").name("Plant 1")
                .status(OrganizationStatus.ACTIVE).build();
        return WorkOrder.builder().workOrderId(UUID.randomUUID()).plant(plant).workOrderNo("WO-1")
                .plannedQuantity(BigDecimal.TEN).status(WorkOrderStatus.RELEASED).build();
    }

    private WorkCenter workCenter() {
        return WorkCenter.builder().workCenterId(UUID.randomUUID()).code("WC-1").name("Work Center 1")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build();
    }

    private WorkOrderOperation operation(WorkOrder workOrder, WorkCenter workCenter, int sequence,
                                          Instant plannedStartAt, Instant plannedEndAt) {
        WorkOrderOperation operation = WorkOrderOperation.builder()
                .workOrderOperationId(UUID.randomUUID())
                .workOrder(workOrder)
                .sequence(sequence)
                .name("Operation " + sequence)
                .workCenterCode(workCenter == null ? "WC-LEGACY" : workCenter.getCode())
                .workCenter(workCenter)
                .setupMinutes(BigDecimal.ZERO)
                .runMinutesPerUnit(BigDecimal.ZERO)
                .plannedStartAt(plannedStartAt)
                .plannedEndAt(plannedEndAt)
                .build();
        operation.setVersion(0L);
        return operation;
    }
}
