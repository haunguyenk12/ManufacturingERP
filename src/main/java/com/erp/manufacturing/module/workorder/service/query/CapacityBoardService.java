package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.shift.service.WorkingInterval;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityBoardLineResponse;
import com.erp.manufacturing.module.workorder.repository.CapacityLoadProjection;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Capacity Board (C2-8, static CRP) — reads schedules {@code WorkOrderService.release()} already
 * wrote, enriched with each Work Center's day capacity/existing-load/utilization. Does not itself
 * schedule anything; that is {@code WorkOrderService}'s job.
 */
@Service
@RequiredArgsConstructor
public class CapacityBoardService {

    /**
     * Load is always computed across these statuses regardless of the board's own {@code status}
     * row filter — the filter narrows which rows are *shown*, not the load denominator, so
     * utilization numbers stay consistent across differently-filtered board views. {@code CANCELLED}
     * is excluded (its schedule no longer represents real load); {@code DRAFT}/{@code PLANNED}/
     * {@code BLOCKED} never have a schedule at all (only {@code release()} writes one). {@code CLOSED}
     * (P6) stays in alongside {@code COMPLETED} for the same reason {@code COMPLETED} is here: its
     * schedule still represents real historical load — closing a work order must not silently rewrite
     * a day's already-reported capacity utilization by making its operations disappear.
     */
    static final List<WorkOrderStatus> LOAD_STATUSES = List.of(
            WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED);

    private final WorkOrderOperationRepository workOrderOperationRepository;
    private final WorkCalendarLookupService workCalendarLookupService;

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_CAPACITY_READ', 'PLANT', #plantId)")
    public PageResult<CapacityBoardLineResponse> getBoard(UUID plantId, LocalDate from, LocalDate to,
                                                           UUID workCenterId, WorkOrderStatus status,
                                                           Pageable pageable) {
        ensureValidRange(from, to);
        Page<WorkOrderOperation> page = workOrderOperationRepository.searchCapacityBoard(
                plantId, workCenterId, status, from, to, pageable);
        DayContext context = buildDayContext(plantId, from, to, page.getContent());
        List<CapacityBoardLineResponse> lines = page.getContent().stream()
                .map(operation -> toLine(operation, context))
                .toList();
        return PageResult.from(new PageImpl<>(lines, pageable, page.getTotalElements()));
    }

    /**
     * Public (not just this package) so {@code ScheduleAdjustmentService} — a different package by
     * design, see its javadoc — can reuse the exact same load/capacity math for its single-operation
     * response instead of duplicating the query. Two endpoints reporting two different numbers for
     * "is this overloaded" would be worse than the cross-package call.
     */
    public DayContext buildDayContext(UUID plantId, LocalDate from, LocalDate to, List<WorkOrderOperation> operations) {
        Map<String, BigDecimal> loadByKey = workOrderOperationRepository
                .aggregateExistingLoad(plantId, from, to, LOAD_STATUSES).stream()
                .collect(Collectors.toMap(
                        p -> key(p.getWorkCenterId(), p.getDay()),
                        CapacityLoadProjection::getLoadMinutes));

        Map<UUID, WorkCenter> workCentersById = operations.stream()
                .map(WorkOrderOperation::getWorkCenter)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(WorkCenter::getWorkCenterId, wc -> wc, (a, b) -> a));

        Map<UUID, Map<LocalDate, List<WorkingInterval>>> windowsByWorkCenter = new HashMap<>();
        Map<UUID, Set<LocalDate>> exceptionsByWorkCenter = new HashMap<>();
        for (WorkCenter workCenter : workCentersById.values()) {
            if (workCenter.getWorkCalendar() == null) {
                continue;
            }
            UUID calendarId = workCenter.getWorkCalendar().getWorkCalendarId();
            windowsByWorkCenter.put(workCenter.getWorkCenterId(),
                    workCalendarLookupService.computeWorkingWindows(calendarId, from, to));
            exceptionsByWorkCenter.put(workCenter.getWorkCenterId(),
                    workCalendarLookupService.findNonWorkingExceptionDates(calendarId, from, to));
        }
        return new DayContext(loadByKey, windowsByWorkCenter, exceptionsByWorkCenter);
    }

    CapacityBoardLineResponse toLine(WorkOrderOperation operation, DayContext context) {
        WorkOrder workOrder = operation.getWorkOrder();
        WorkCenter workCenter = operation.getWorkCenter();
        LocalDate day = attributedDay(operation, workOrder);
        BigDecimal operationMinutes = operation.getSetupMinutes()
                .add(operation.getRunMinutesPerUnit().multiply(workOrder.getPlannedQuantity()));
        BigDecimal loadMinutes = context.loadMinutesByKey()
                .getOrDefault(key(workCenter.getWorkCenterId(), day), BigDecimal.ZERO);
        BigDecimal capacityMinutes = dayCapacityMinutes(context, workCenter, day);
        boolean exceptionApplies = context.exceptionDatesByWorkCenter()
                .getOrDefault(workCenter.getWorkCenterId(), Set.of())
                .contains(day);
        BigDecimal utilizationPercent = utilizationPercent(loadMinutes, capacityMinutes);
        boolean overload = capacityMinutes != null && loadMinutes.compareTo(capacityMinutes) > 0;

        return new CapacityBoardLineResponse(
                operation.getWorkOrderOperationId(),
                operation.getSequence(),
                operation.getName(),
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                workOrder.getStatus().name(),
                workCenter.getWorkCenterId(),
                workCenter.getCode(),
                workCenter.getName(),
                workOrder.getPlant().getPlantId(),
                workOrder.getPlant().getCode(),
                operation.getPlannedStartAt(),
                operation.getPlannedEndAt(),
                operation.getSetupMinutes(),
                operation.getRunMinutesPerUnit(),
                operationMinutes,
                capacityMinutes,
                loadMinutes,
                utilizationPercent,
                overload,
                exceptionApplies,
                operation.getVersion());
    }

    public static LocalDate attributedDay(WorkOrderOperation operation, WorkOrder workOrder) {
        return operation.getPlannedStartAt()
                .atZone(ZoneId.of(workOrder.getPlant().getTimezone()))
                .toLocalDate();
    }

    /** {@code null} when the Work Center has no calendar — capacity is unknown, not zero. */
    public static BigDecimal dayCapacityMinutes(DayContext context, WorkCenter workCenter, LocalDate day) {
        Map<LocalDate, List<WorkingInterval>> windows = context.windowsByWorkCenter().get(workCenter.getWorkCenterId());
        if (windows == null) {
            return null;
        }
        long minutes = windows.getOrDefault(day, List.of()).stream()
                .mapToLong(interval -> Duration.between(interval.start(), interval.end()).toMinutes())
                .sum();
        return BigDecimal.valueOf(minutes).multiply(BigDecimal.valueOf(workCenter.getCapacityUnits()));
    }

    public static BigDecimal utilizationPercent(BigDecimal loadMinutes, BigDecimal capacityMinutes) {
        if (capacityMinutes == null || capacityMinutes.signum() == 0) {
            return null;
        }
        return loadMinutes.multiply(BigDecimal.valueOf(100)).divide(capacityMinutes, 2, RoundingMode.HALF_UP);
    }

    public static String key(UUID workCenterId, LocalDate day) {
        return workCenterId + "|" + day;
    }

    private void ensureValidRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, "'from' and 'to' are required");
        }
        if (from.isAfter(to)) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT, "'from' must not be after 'to'");
        }
    }

    public record DayContext(Map<String, BigDecimal> loadMinutesByKey,
                              Map<UUID, Map<LocalDate, List<WorkingInterval>>> windowsByWorkCenter,
                              Map<UUID, Set<LocalDate>> exceptionDatesByWorkCenter) {
    }
}
