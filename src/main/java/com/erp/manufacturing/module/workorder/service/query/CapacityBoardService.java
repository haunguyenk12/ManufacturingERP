package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.shift.service.TimeInterval;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityBoardLineResponse;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityLoadResponse;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityPeriodResponse;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Capacity Board (C2-8, static CRP) — reads schedules {@code WorkOrderService.release()} already
 * wrote, enriched with each Work Center's day capacity/existing-load/utilization. Does not itself
 * schedule anything; that is {@code WorkOrderService}'s job.
 *
 * <p>G5 (D9): load is now allocated by the share of an operation's working time that falls in each
 * day, not dropped whole onto its start day — see {@link CapacityLoadCalculator}.
 */
@Service
@RequiredArgsConstructor
public class CapacityBoardService {

    /** FE Q11: a period is at most 90 calendar days, both ends included. */
    public static final int MAX_PERIOD_DAYS = 90;

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
    private final CapacityLoadCalculator calculator;
    private final OrganizationLookupService organizationLookupService;
    private final WorkCenterLookupService workCenterLookupService;

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_CAPACITY_READ', 'PLANT', #plantId)")
    public PageResult<CapacityBoardLineResponse> getBoard(UUID plantId, LocalDate from, LocalDate to,
                                                           UUID workCenterId, WorkOrderStatus status,
                                                           Pageable pageable) {
        ensureValidRange(from, to);
        ZoneId zone = ZoneId.of(organizationLookupService.getPlant(plantId).getTimezone());
        Page<WorkOrderOperation> page = workOrderOperationRepository.searchCapacityBoard(
                plantId, workCenterId, status, startOf(from, zone), startOf(to.plusDays(1), zone), pageable);
        DayContext context = buildDayContext(plantId, from, to, page.getContent());
        List<CapacityBoardLineResponse> lines = page.getContent().stream()
                .map(operation -> toLine(operation, context))
                .toList();
        return PageResult.from(new PageImpl<>(lines, pageable, page.getTotalElements()));
    }

    /**
     * Load of every work center of the plant over {@code [periodStart, periodEnd]} (G5, FE R11): the
     * ACTIVE work centers plus any other one that still carries load in the period. One query for the
     * operations, one calendar expansion per work center (rule C14).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_CAPACITY_READ', 'PLANT', #plantId)")
    public CapacityPeriodResponse getPeriodLoads(UUID plantId, LocalDate periodStart, LocalDate periodEnd,
                                                 UUID workCenterId) {
        ensurePeriod(periodStart, periodEnd);
        Plant plant = organizationLookupService.getPlant(plantId);
        ZoneId zone = ZoneId.of(plant.getTimezone());
        Instant from = startOf(periodStart, zone);
        Instant to = startOf(periodEnd.plusDays(1), zone);

        List<WorkOrderOperation> loadOperations = calculator.loadOperations(plantId, from, to);
        Map<UUID, WorkCenter> workCenters = new LinkedHashMap<>();
        workCenterLookupService.findActiveWorkCenters(plantId)
                .forEach(workCenter -> workCenters.put(workCenter.getWorkCenterId(), workCenter));
        loadOperations.forEach(operation -> workCenters.putIfAbsent(
                operation.getWorkCenter().getWorkCenterId(), operation.getWorkCenter()));
        if (workCenterId != null) {
            workCenters.keySet().retainAll(Set.of(workCenterId));
        }

        Map<UUID, List<TimeInterval>> windows = calculator.windows(workCenters.values(),
                spanStart(from, loadOperations), spanEnd(to, loadOperations), zone);
        Map<UUID, BigDecimal> base = CapacityLoadCalculator.baseLoad(loadOperations, windows, from, to);
        List<CapacityLoadResponse> loads = workCenters.values().stream()
                .sorted(Comparator.comparing(WorkCenter::getCode))
                .map(workCenter -> CapacityLoadCalculator.loadLine(workCenter,
                        windows.get(workCenter.getWorkCenterId()), from, to,
                        base.getOrDefault(workCenter.getWorkCenterId(), BigDecimal.ZERO),
                        BigDecimal.ZERO, BigDecimal.ZERO))
                .toList();
        return new CapacityPeriodResponse(plantId, plant.getTimezone(), periodStart, periodEnd,
                Instant.now().truncatedTo(ChronoUnit.MICROS), loads);
    }

    /**
     * Public (not just this package) so {@code ScheduleAdjustmentService} — a different package by
     * design, see its javadoc — can reuse the exact same load/capacity math for its single-operation
     * response instead of duplicating the query. Two endpoints reporting two different numbers for
     * "is this overloaded" would be worse than the cross-package call.
     */
    public DayContext buildDayContext(UUID plantId, LocalDate from, LocalDate to, List<WorkOrderOperation> operations) {
        ZoneId zone = ZoneId.of(organizationLookupService.getPlant(plantId).getTimezone());
        Instant rangeStart = startOf(from, zone);
        Instant rangeEnd = startOf(to.plusDays(1), zone);
        List<WorkOrderOperation> loadOperations = calculator.loadOperations(plantId, rangeStart, rangeEnd);

        Map<UUID, WorkCenter> workCenters = new HashMap<>();
        for (WorkOrderOperation operation : concat(loadOperations, operations)) {
            if (operation.getWorkCenter() != null) {
                workCenters.putIfAbsent(operation.getWorkCenter().getWorkCenterId(), operation.getWorkCenter());
            }
        }
        List<WorkOrderOperation> everything = concat(loadOperations, operations);
        Map<UUID, List<TimeInterval>> windows = calculator.windows(workCenters.values(),
                spanStart(rangeStart, everything), spanEnd(rangeEnd, everything), zone);

        Map<String, BigDecimal> loadByKey = new HashMap<>();
        for (WorkOrderOperation operation : loadOperations) {
            UUID workCenterId = operation.getWorkCenter().getWorkCenterId();
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                BigDecimal share = CapacityLoadCalculator.allocate(operation, windows.get(workCenterId),
                        startOf(day, zone), startOf(day.plusDays(1), zone));
                if (share.signum() > 0) {
                    loadByKey.merge(key(workCenterId, day), share, BigDecimal::add);
                }
            }
        }

        Map<UUID, Set<LocalDate>> exceptionsByWorkCenter = new HashMap<>();
        for (WorkCenter workCenter : workCenters.values()) {
            if (workCenter.getWorkCalendar() != null) {
                exceptionsByWorkCenter.put(workCenter.getWorkCenterId(),
                        workCalendarLookupService.findNonWorkingExceptionDates(
                                workCenter.getWorkCalendar().getWorkCalendarId(), from, to));
            }
        }
        return new DayContext(zone, from, to, loadByKey, windows, exceptionsByWorkCenter);
    }

    CapacityBoardLineResponse toLine(WorkOrderOperation operation, DayContext context) {
        WorkOrder workOrder = operation.getWorkOrder();
        WorkCenter workCenter = operation.getWorkCenter();
        // An operation that started before the board's range is shown against the range's first day.
        LocalDate day = later(attributedDay(operation, workOrder), context.from());
        BigDecimal operationMinutes = CapacityLoadCalculator.requiredMinutes(operation);
        BigDecimal loadMinutes = context.loadMinutesByKey()
                .getOrDefault(key(workCenter.getWorkCenterId(), day), BigDecimal.ZERO);
        BigDecimal capacityMinutes = dayCapacityMinutes(context, workCenter, day);
        boolean exceptionApplies = context.exceptionDatesByWorkCenter()
                .getOrDefault(workCenter.getWorkCenterId(), Set.of())
                .contains(day);
        BigDecimal utilizationPercent = utilizationPercent(loadMinutes, capacityMinutes);
        boolean overload = capacityMinutes != null && loadMinutes.compareTo(capacityMinutes) > 0;
        BigDecimal inPeriod = CapacityLoadCalculator.allocate(operation,
                context.windowsByWorkCenter().get(workCenter.getWorkCenterId()),
                startOf(context.from(), context.zone()), startOf(context.to().plusDays(1), context.zone()));

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
                operation.getVersion(),
                inPeriod);
    }

    public static LocalDate attributedDay(WorkOrderOperation operation, WorkOrder workOrder) {
        return operation.getPlannedStartAt()
                .atZone(ZoneId.of(workOrder.getPlant().getTimezone()))
                .toLocalDate();
    }

    /**
     * Working minutes of the work center's calendar that fall inside the day, times its capacity
     * units. G5: the tail of an overnight shift belongs to the day it runs in, not to the day the
     * shift started. {@code null} when the Work Center has no calendar — unknown, not zero.
     */
    public static BigDecimal dayCapacityMinutes(DayContext context, WorkCenter workCenter, LocalDate day) {
        List<TimeInterval> windows = context.windowsByWorkCenter().get(workCenter.getWorkCenterId());
        if (windows == null) {
            return null;
        }
        return CapacityLoadCalculator.workingMinutes(windows,
                        startOf(day, context.zone()), startOf(day.plusDays(1), context.zone()))
                .multiply(BigDecimal.valueOf(workCenter.getCapacityUnits()));
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

    static Instant startOf(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant();
    }

    /** FE Q11: both dates required, start not after end, at most {@link #MAX_PERIOD_DAYS} days inclusive. */
    static void ensurePeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart.isAfter(periodEnd)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("periodEnd", "must not be before periodStart"));
        }
        if (ChronoUnit.DAYS.between(periodStart, periodEnd) + 1 > MAX_PERIOD_DAYS) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("periodEnd", "a period is at most " + MAX_PERIOD_DAYS + " days, both ends included"));
        }
    }

    private void ensureValidRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, "'from' and 'to' are required");
        }
        if (from.isAfter(to)) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT, "'from' must not be after 'to'");
        }
    }

    /** Calendars must cover every operation's whole span, or allocation would divide by a short total. */
    static Instant spanStart(Instant start, List<WorkOrderOperation> operations) {
        return operations.stream().map(WorkOrderOperation::getPlannedStartAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).filter(earliest -> earliest.isBefore(start)).orElse(start);
    }

    static Instant spanEnd(Instant end, List<WorkOrderOperation> operations) {
        return operations.stream().map(WorkOrderOperation::getPlannedEndAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).filter(latest -> latest.isAfter(end)).orElse(end);
    }

    private static List<WorkOrderOperation> concat(List<WorkOrderOperation> a, List<WorkOrderOperation> b) {
        List<WorkOrderOperation> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    public record DayContext(ZoneId zone,
                             LocalDate from,
                             LocalDate to,
                             Map<String, BigDecimal> loadMinutesByKey,
                             Map<UUID, List<TimeInterval>> windowsByWorkCenter,
                             Map<UUID, Set<LocalDate>> exceptionDatesByWorkCenter) {
    }
}
