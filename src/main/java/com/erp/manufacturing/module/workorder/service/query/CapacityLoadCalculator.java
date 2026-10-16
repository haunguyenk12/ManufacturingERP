package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.shift.service.TimeInterval;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityLoadResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The load arithmetic shared by the capacity board, the period loads and the preview (G5, D9).
 *
 * <p>🔴 The rule this class exists for: an operation's minutes are <em>allocated</em> to a period in
 * proportion to how much of its working time falls inside it — not dropped whole onto the day it
 * starts (what C2-8 did, §0.30 #4). A three-day operation used to show as one day of false overload
 * and two days of false idle. Proportional to working time, not wall-clock time, so a night shift or
 * a weekend inside the span does not attract load it never carries. Without a calendar there is no
 * working time to weigh by, and the wall clock is the only honest measure left.
 *
 * <p>Load minutes are <em>capacity</em> minutes ({@code setup + run × quantity}), the same unit as
 * available minutes (working minutes × {@code capacityUnits}) — never elapsed minutes. Dividing by
 * capacity units happens once, when an elapsed schedule is built (preview), never when comparing.
 */
@Component
@RequiredArgsConstructor
public class CapacityLoadCalculator {

    static final int SCALE = 6;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** FE Q15: utilisation from 85 % up to 100 % is "near capacity". */
    private static final BigDecimal NEAR_CAPACITY_PERCENT = BigDecimal.valueOf(85);

    private final WorkOrderOperationRepository workOrderOperationRepository;
    private final WorkCalendarLookupService workCalendarLookupService;

    /** Scheduled operations of load-carrying work orders on the plant whose window overlaps {@code [from, to)}. */
    public List<WorkOrderOperation> loadOperations(UUID plantId, Instant from, Instant to) {
        return workOrderOperationRepository.findScheduledOverlapping(
                plantId, from, to, CapacityBoardService.LOAD_STATUSES);
    }

    /**
     * Merged working intervals over {@code [from, to)} for every work center that has a calendar. A
     * work center without one is absent from the map — its capacity is unknown, not zero.
     */
    public Map<UUID, List<TimeInterval>> windows(Collection<WorkCenter> workCenters, Instant from, Instant to,
                                                 ZoneId zone) {
        Map<UUID, List<TimeInterval>> windows = new HashMap<>();
        for (WorkCenter workCenter : workCenters) {
            if (workCenter.getWorkCalendar() != null && !windows.containsKey(workCenter.getWorkCenterId())) {
                windows.put(workCenter.getWorkCenterId(), workCalendarLookupService.computeWorkingIntervals(
                        workCenter.getWorkCalendar().getWorkCalendarId(), from, to, zone));
            }
        }
        return windows;
    }

    public static BigDecimal requiredMinutes(WorkOrderOperation operation) {
        return operation.getSetupMinutes()
                .add(operation.getRunMinutesPerUnit().multiply(operation.getWorkOrder().getPlannedQuantity()));
    }

    /** Working minutes of {@code windows} inside {@code [from, to)}. */
    public static BigDecimal workingMinutes(List<TimeInterval> windows, Instant from, Instant to) {
        BigDecimal total = BigDecimal.ZERO;
        for (TimeInterval window : windows) {
            TimeInterval overlap = window.clip(from, to);
            if (overlap != null) {
                total = total.add(overlap.minutes());
            }
        }
        return total;
    }

    /**
     * The part of the operation's required minutes that belongs to {@code [from, to)}. {@code windows}
     * must cover the operation's whole span, or the denominator would be short.
     */
    public static BigDecimal allocate(WorkOrderOperation operation, List<TimeInterval> windows,
                                      Instant from, Instant to) {
        BigDecimal required = requiredMinutes(operation);
        Instant start = operation.getPlannedStartAt();
        Instant end = operation.getPlannedEndAt();
        if (windows != null) {
            BigDecimal total = workingMinutes(windows, start, end);
            if (total.signum() > 0) {
                BigDecimal inside = workingMinutes(windows, later(start, from), earlier(end, to));
                return required.multiply(inside).divide(total, SCALE, RoundingMode.HALF_UP);
            }
        }
        long totalSeconds = Duration.between(start, end).toSeconds();
        if (totalSeconds <= 0) {
            return !start.isBefore(from) && start.isBefore(to) ? required.setScale(SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(SCALE);
        }
        long insideSeconds = Math.max(0, Duration.between(later(start, from), earlier(end, to)).toSeconds());
        return required.multiply(BigDecimal.valueOf(insideSeconds))
                .divide(BigDecimal.valueOf(totalSeconds), SCALE, RoundingMode.HALF_UP);
    }

    /** Base load of each work center inside {@code [from, to)} from already scheduled operations. */
    public static Map<UUID, BigDecimal> baseLoad(List<WorkOrderOperation> loadOperations,
                                                 Map<UUID, List<TimeInterval>> windows, Instant from, Instant to) {
        Map<UUID, BigDecimal> base = new HashMap<>();
        for (WorkOrderOperation operation : loadOperations) {
            UUID workCenterId = operation.getWorkCenter().getWorkCenterId();
            base.merge(workCenterId, allocate(operation, windows.get(workCenterId), from, to), BigDecimal::add);
        }
        return base;
    }

    /**
     * One work center's load line (FE §3.6): demand = base + the full required minutes of a preview,
     * allocated = base + only the preview's in-period part. {@code windows == null} means "no calendar":
     * available, overload and utilisation are unknown and reported as {@code null}.
     */
    public static CapacityLoadResponse loadLine(WorkCenter workCenter, List<TimeInterval> windows,
                                                Instant from, Instant to, BigDecimal base,
                                                BigDecimal previewRequired, BigDecimal previewInPeriod) {
        BigDecimal baseMinutes = scaled(base);
        BigDecimal demand = scaled(baseMinutes.add(previewRequired));
        BigDecimal allocated = scaled(baseMinutes.add(previewInPeriod));
        BigDecimal available = windows == null ? null
                : scaled(workingMinutes(windows, from, to).multiply(BigDecimal.valueOf(workCenter.getCapacityUnits())));
        BigDecimal overload = available == null ? null : scaled(demand.subtract(available).max(BigDecimal.ZERO));
        BigDecimal utilization = available == null || available.signum() == 0 ? null
                : demand.multiply(HUNDRED).divide(available, 2, RoundingMode.HALF_UP);
        return new CapacityLoadResponse(
                workCenter.getWorkCenterId(),
                workCenter.getCode(),
                workCenter.getName(),
                workCenter.getCapacityUnits(),
                baseMinutes,
                scaled(previewRequired),
                demand,
                allocated,
                available,
                overload,
                utilization,
                status(available, demand, overload, utilization));
    }

    /**
     * FE Q15: OVERLOADED when demand exceeds what is available — including any demand at all on zero
     * capacity; NEAR_CAPACITY from 85 %; {@code null} when there is no calendar to judge by.
     */
    static String status(BigDecimal available, BigDecimal demand, BigDecimal overload, BigDecimal utilization) {
        if (available == null) {
            return null;
        }
        if (overload.signum() > 0 || (available.signum() == 0 && demand.signum() > 0)) {
            return "OVERLOADED";
        }
        if (utilization != null && utilization.compareTo(NEAR_CAPACITY_PERCENT) >= 0) {
            return "NEAR_CAPACITY";
        }
        return "AVAILABLE";
    }

    private static BigDecimal scaled(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
