package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentRequest;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.service.query.CapacityBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Manual schedule override on a single {@link WorkOrderOperation} (C2-8, gap doc §3.6). Never
 * auto-shifts sibling operations (decision #3, {@code NEXT_PHASE_PLAN.md}) — conflicts are surfaced
 * as advisory flags on {@link ScheduleAdjustmentResponse}, not reasons to reject the request.
 */
@Service
@RequiredArgsConstructor
public class ScheduleAdjustmentService {

    private final WorkOrderOperationRepository workOrderOperationRepository;
    private final CapacityBoardService capacityBoardService;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_CAPACITY_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_OPERATION_SCHEDULE_ADJUSTED, entityType = "WorkOrderOperation",
            entityIdExpression = "operationId.toString()")
    public ScheduleAdjustmentResponse adjust(UUID workOrderId, UUID operationId, ScheduleAdjustmentRequest request) {
        WorkOrderOperation operation = workOrderOperationRepository
                .findByWorkOrderOperationIdAndWorkOrderWorkOrderId(operationId, workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order operation", operationId));

        // Fail fast (rule C9), before touching anything: no work center / never scheduled / stale
        // version / nonsensical window are all reasons to refuse outright, unlike sequence/calendar/
        // capacity below which are informational only.
        if (operation.getWorkCenter() == null) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Operation has no work center to schedule against");
        }
        if (operation.getPlannedStartAt() == null) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Operation has not been scheduled yet — release the work order first");
        }
        if (!request.expectedVersion().equals(operation.getVersion())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Work order operation was modified by another request");
        }
        if (!request.plannedEndAt().isAfter(request.plannedStartAt())) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "plannedEndAt must be after plannedStartAt");
        }

        boolean sequenceConflict = hasSequenceConflict(operation, request);

        operation.setPlannedStartAt(request.plannedStartAt());
        operation.setPlannedEndAt(request.plannedEndAt());
        operation.setScheduleAdjustmentReason(request.reason().trim());
        WorkOrderOperation saved = workOrderOperationRepository.save(operation);

        WorkOrder workOrder = saved.getWorkOrder();
        LocalDate day = CapacityBoardService.attributedDay(saved, workOrder);
        // Recomputed AFTER persisting: the load aggregate reads current state, so it already
        // reflects this operation's new window — no need to simulate "what if" before writing.
        CapacityBoardService.DayContext context = capacityBoardService.buildDayContext(
                workOrder.getPlant().getPlantId(), day, day, List.of(saved));
        BigDecimal capacityMinutes = CapacityBoardService.dayCapacityMinutes(context, saved.getWorkCenter(), day);
        BigDecimal loadMinutes = context.loadMinutesByKey()
                .getOrDefault(CapacityBoardService.key(saved.getWorkCenter().getWorkCenterId(), day), BigDecimal.ZERO);
        boolean calendarConflict = saved.getWorkCenter().getWorkCalendar() != null
                && (capacityMinutes == null || capacityMinutes.signum() == 0);
        boolean capacityOverload = capacityMinutes != null && loadMinutes.compareTo(capacityMinutes) > 0;

        return new ScheduleAdjustmentResponse(
                saved.getWorkOrderOperationId(),
                saved.getPlannedStartAt(),
                saved.getPlannedEndAt(),
                saved.getScheduleAdjustmentReason(),
                saved.getVersion(),
                sequenceConflict,
                calendarConflict,
                capacityOverload,
                capacityMinutes,
                loadMinutes,
                CapacityBoardService.utilizationPercent(loadMinutes, capacityMinutes));
    }

    /**
     * Advisory only (decision #3) — does not block the write. Only compares against siblings that
     * already carry a schedule of their own; an unscheduled sibling has nothing to conflict with.
     */
    private boolean hasSequenceConflict(WorkOrderOperation operation, ScheduleAdjustmentRequest request) {
        List<WorkOrderOperation> siblings = workOrderOperationRepository
                .findByWorkOrderWorkOrderIdOrderBySequenceAsc(operation.getWorkOrder().getWorkOrderId());
        WorkOrderOperation predecessor = null;
        WorkOrderOperation successor = null;
        for (WorkOrderOperation sibling : siblings) {
            if (sibling.getWorkOrderOperationId().equals(operation.getWorkOrderOperationId())
                    || sibling.getPlannedStartAt() == null) {
                continue;
            }
            if (sibling.getSequence() < operation.getSequence()
                    && (predecessor == null || sibling.getSequence() > predecessor.getSequence())) {
                predecessor = sibling;
            }
            if (sibling.getSequence() > operation.getSequence()
                    && (successor == null || sibling.getSequence() < successor.getSequence())) {
                successor = sibling;
            }
        }
        boolean beforePredecessor = predecessor != null
                && request.plannedStartAt().isBefore(predecessor.getPlannedEndAt());
        boolean afterSuccessor = successor != null
                && request.plannedEndAt().isAfter(successor.getPlannedStartAt());
        return beforePredecessor || afterSuccessor;
    }
}
