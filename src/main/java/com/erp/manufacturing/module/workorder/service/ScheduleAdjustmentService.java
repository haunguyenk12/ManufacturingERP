package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentRequest;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentResponse;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.service.query.CapacityBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Manual schedule override on a single {@link WorkOrderOperation} (C2-8, gap doc §3.6). Never
 * auto-shifts sibling operations (decision #3, {@code NEXT_PHASE_PLAN.md}).
 *
 * <p>G5 (D10) reverses one half of B90: overlapping the previous or next operation of the same work
 * order is now refused with {@code OPERATION_SEQUENCE_CONFLICT} (409) and nothing is written — the
 * user decided the board must not hold a sequence it knows to be impossible. Calendar and capacity
 * problems stay advisory flags: those are about other work orders, which this command never touches.
 */
@Service
@RequiredArgsConstructor
public class ScheduleAdjustmentService {

    /** FE Q16: only a work order that is running or about to run, and only an operation not yet started. */
    private static final List<WorkOrderStatus> ADJUSTABLE_STATUSES =
            List.of(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS);

    private final WorkOrderOperationRepository workOrderOperationRepository;
    private final ProductionExecutionRepository productionExecutionRepository;
    private final CapacityBoardService capacityBoardService;
    private final WorkCalendarLookupService workCalendarLookupService;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_CAPACITY_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_OPERATION_SCHEDULE_ADJUSTED, entityType = "WorkOrderOperation",
            entityIdExpression = "operationId.toString()")
    public ScheduleAdjustmentResponse adjust(UUID workOrderId, UUID operationId, ScheduleAdjustmentRequest request) {
        WorkOrderOperation operation = workOrderOperationRepository
                .findByWorkOrderOperationIdAndWorkOrderWorkOrderId(operationId, workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order operation", operationId));
        WorkOrder workOrder = operation.getWorkOrder();

        // Fail fast (rule C9), before touching anything.
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
        if (!ADJUSTABLE_STATUSES.contains(workOrder.getStatus())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a RELEASED or IN_PROGRESS work order can be rescheduled; this one is "
                            + workOrder.getStatus());
        }
        if (productionExecutionRepository.existsByOperationWorkOrderOperationId(operationId)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Output was already reported for this operation; it can no longer be rescheduled");
        }
        Instant plannedEndAt = request.plannedEndAt() != null
                ? request.plannedEndAt()
                : computeEnd(operation, request.plannedStartAt());
        if (!plannedEndAt.isAfter(request.plannedStartAt())) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "plannedEndAt must be after plannedStartAt");
        }
        ensureNoSequenceConflict(operation, request.plannedStartAt(), plannedEndAt);

        operation.setPlannedStartAt(request.plannedStartAt());
        operation.setPlannedEndAt(plannedEndAt);
        operation.setScheduleAdjustmentReason(request.reason().trim());
        // G8a (D9): a hand-made change is a new committed schedule. The calendar revision stays the one
        // release computed on — nothing was recomputed from the calendars here. Flushed with the operation.
        workOrder.commitSchedule(workOrder.getScheduleCalendarRevision(),
                workOrder.getScheduleTimeZone() != null ? workOrder.getScheduleTimeZone() : workOrder.getPlant().getTimezone(),
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        // saveAndFlush: the response carries the bumped version (lesson of CLAUDE.md §0.39).
        WorkOrderOperation saved = workOrderOperationRepository.saveAndFlush(operation);

        LocalDate day = CapacityBoardService.attributedDay(saved, workOrder);
        // Recomputed AFTER persisting: the load query reads current state, so it already reflects this
        // operation's new window — no need to simulate "what if" before writing.
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
                // Always false since G5: a conflicting window is refused before anything is written.
                false,
                calendarConflict,
                capacityOverload,
                capacityMinutes,
                loadMinutes,
                CapacityBoardService.utilizationPercent(loadMinutes, capacityMinutes));
    }

    /**
     * Same rule release uses ({@code WorkOrderService.scheduleOperations}): the operation's own minutes,
     * walked over its work center's calendar when there is one, continuous time otherwise.
     */
    private Instant computeEnd(WorkOrderOperation operation, Instant start) {
        long durationMinutes = operation.getSetupMinutes()
                .add(operation.getRunMinutesPerUnit().multiply(operation.getWorkOrder().getPlannedQuantity()))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
        WorkCenter workCenter = operation.getWorkCenter();
        if (workCenter.getWorkCalendar() == null) {
            return start.plus(durationMinutes, ChronoUnit.MINUTES);
        }
        ZoneId zone = ZoneId.of(operation.getWorkOrder().getPlant().getTimezone());
        return workCalendarLookupService.computeEndInstant(
                workCenter.getWorkCalendar().getWorkCalendarId(), start, zone, durationMinutes);
    }

    /**
     * G5 (D10): the new window may not start before the previous operation ends, nor end after the next
     * one starts. Only siblings that carry a schedule count; an unscheduled sibling has nothing to
     * conflict with.
     */
    private void ensureNoSequenceConflict(WorkOrderOperation operation, Instant start, Instant end) {
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
        if (predecessor != null && start.isBefore(predecessor.getPlannedEndAt())) {
            throw ExceptionFactory.custom(BusinessErrorCode.OPERATION_SEQUENCE_CONFLICT,
                    "Operation would start at " + start + ", before operation " + predecessor.getSequence()
                            + " ends at " + predecessor.getPlannedEndAt());
        }
        if (successor != null && end.isAfter(successor.getPlannedStartAt())) {
            throw ExceptionFactory.custom(BusinessErrorCode.OPERATION_SEQUENCE_CONFLICT,
                    "Operation would end at " + end + ", after operation " + successor.getSequence()
                            + " starts at " + successor.getPlannedStartAt());
        }
    }
}
