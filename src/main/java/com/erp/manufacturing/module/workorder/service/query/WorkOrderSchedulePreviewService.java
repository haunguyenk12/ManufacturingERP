package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.schedule.WorkOrderSchedulePreviewRequest;
import com.erp.manufacturing.module.workorder.dto.schedule.WorkOrderSchedulePreviewResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderScheduler;
import com.erp.manufacturing.module.workorder.service.WorkOrderVersions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Schedule preview of one work order (G8a, FE §3.3). Read-only end to end: it computes what release would
 * commit — the same {@link WorkOrderScheduler}, the same start rule — and reports where that window meets
 * load other work orders have already committed on the same work center. The overlap is a warning: release
 * schedules with infinite capacity and never waits for other load.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderSchedulePreviewService {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderScheduler scheduler;
    private final CapacityLoadCalculator calculator;

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_READ', #workOrderId)")
    public WorkOrderSchedulePreviewResponse preview(UUID workOrderId, WorkOrderSchedulePreviewRequest request) {
        WorkOrder workOrder = workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId));
        if (request != null && request.expectedVersion() != null) {
            WorkOrderVersions.ensure(workOrder, request.expectedVersion(), "expectedVersion");
        }
        if (!workOrder.canRelease()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, "The schedule of a "
                    + workOrder.getStatus() + " work order is already committed or closed; read its operations");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant start = request != null && request.plannedStartAt() != null ? request.plannedStartAt()
                : workOrder.getPlannedStartAt() != null ? workOrder.getPlannedStartAt() : now;
        BigDecimal quantity = request != null && request.plannedQuantity() != null
                ? request.plannedQuantity() : workOrder.getPlannedQuantity();

        WorkOrderScheduler.Schedule schedule = scheduler.compute(workOrder, start, quantity);
        List<WorkOrderSchedulePreviewResponse.Warning> warnings = new ArrayList<>();
        if (schedule.slots().isEmpty()) {
            warnings.add(new WorkOrderSchedulePreviewResponse.Warning("NO_ROUTING", null,
                    "The work order has no routing operations; there is nothing to schedule"));
        }
        List<WorkOrderOperation> otherLoad = schedule.slots().isEmpty() ? List.of()
                : calculator.loadOperations(workOrder.getPlant().getPlantId(), schedule.slots().get(0).start(),
                        schedule.slots().get(schedule.slots().size() - 1).end()).stream()
                .filter(operation -> !operation.getWorkOrder().getWorkOrderId().equals(workOrderId))
                .toList();

        List<WorkOrderSchedulePreviewResponse.Row> rows = new ArrayList<>();
        for (WorkOrderScheduler.Slot slot : schedule.slots()) {
            WorkOrderOperation operation = slot.operation();
            UUID workCenterId = operation.getWorkCenter() == null ? null : operation.getWorkCenter().getWorkCenterId();
            int overlapping = workCenterId == null ? 0 : (int) otherLoad.stream()
                    .filter(other -> other.getWorkCenter().getWorkCenterId().equals(workCenterId)
                            && other.getPlannedStartAt().isBefore(slot.end())
                            && other.getPlannedEndAt().isAfter(slot.start()))
                    .count();
            if (workCenterId == null) {
                warnings.add(new WorkOrderSchedulePreviewResponse.Warning("NO_WORK_CENTER",
                        operation.getWorkOrderOperationId(), "Operation " + operation.getSequence()
                                + " has no work center; it is placed in continuous time and is invisible to capacity"));
            } else if (!slot.onCalendar()) {
                warnings.add(new WorkOrderSchedulePreviewResponse.Warning("NO_CALENDAR",
                        operation.getWorkOrderOperationId(), "Work center " + operation.getWorkCenterCode()
                                + " has no calendar; the operation is placed in continuous time"));
            }
            if (overlapping > 0) {
                warnings.add(new WorkOrderSchedulePreviewResponse.Warning("OVERLAP",
                        operation.getWorkOrderOperationId(), overlapping + " operation(s) of other work orders "
                                + "are already scheduled on " + operation.getWorkCenterCode() + " in this window"));
            }
            rows.add(new WorkOrderSchedulePreviewResponse.Row(operation.getWorkOrderOperationId(),
                    operation.getSequence(), operation.getName(), workCenterId, operation.getWorkCenterCode(),
                    slot.start(), slot.end(), slot.workMinutes(), slot.onCalendar(), overlapping));
        }
        return new WorkOrderSchedulePreviewResponse(workOrderId, workOrder.getVersion(), schedule.timeZone(),
                schedule.calendarRevision(), quantity, start, rows, warnings, now);
    }
}
