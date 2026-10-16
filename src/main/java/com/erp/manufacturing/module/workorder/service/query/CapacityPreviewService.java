package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.service.RoutingLookupService;
import com.erp.manufacturing.module.shift.service.TimeInterval;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityLoadResponse;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityPreviewRequest;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityPreviewResponse;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityPreviewResponse.Segment;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityPreviewResponse.SourceRef;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What-if schedule of an ACTIVE routing for a quantity (G5, FE §3.6, R10/A27–A30). Read-only from
 * end to end — {@code @Transactional(readOnly = true)}, no repository write, no work order, no
 * reservation, no movement (A30). It answers "when would this finish, and how loaded would each work
 * center be", never "book it".
 *
 * <p>Operations run strictly one after another by sequence (FE Q17), each spread over its work
 * center's working time with {@code capacityUnits} in parallel: elapsed = required / units. This is
 * infinite-capacity scheduling — it does not wait for existing load, it only reports it. So the
 * result can show more allocated than available; that is the warning the board exists to give.
 */
@Service
@RequiredArgsConstructor
public class CapacityPreviewService {

    /** FE §3.6: the schedule may run past the period, up to this many days after its end. */
    static final int HORIZON_DAYS_AFTER_PERIOD = 180;

    private final OrganizationLookupService organizationLookupService;
    private final RoutingLookupService routingLookupService;
    private final BomLookupService bomLookupService;
    private final CapacityLoadCalculator calculator;

    @Transactional(readOnly = true)
    @PreAuthorize("@capacityPermissionGuard.canPreview(authentication, #plantId)")
    public CapacityPreviewResponse preview(UUID plantId, CapacityPreviewRequest request) {
        CapacityBoardService.ensurePeriod(request.periodStart(), request.periodEnd());
        Plant plant = organizationLookupService.getActivePlant(plantId);
        ZoneId zone = ZoneId.of(plant.getTimezone());
        Instant from = CapacityBoardService.startOf(request.periodStart(), zone);
        Instant to = CapacityBoardService.startOf(request.periodEnd().plusDays(1), zone);
        if (request.requestedStart().isBefore(from) || !request.requestedStart().isBefore(to)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("requestedStart", "must fall inside the period"));
        }

        UUID companyId = plant.getCompany().getCompanyId();
        RoutingHeader routing = routingLookupService.findRoutingWithOperations(request.routingId())
                // Another company's routing is answered like a missing one.
                .filter(found -> found.getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Routing", request.routingId()));
        ensureRoutingUsable(routing, request.requestedStart(), plantId);
        BomHeader bom = bomLookupService.getActiveBom(companyId, routing.getItem().getItemId());
        if (bom.getEffectiveFrom() != null && bom.getEffectiveFrom().isAfter(request.requestedStart())) {
            throw ExceptionFactory.custom(BusinessErrorCode.EFFECTIVE_DATE_FUTURE,
                    "The ACTIVE BOM is effective only from " + bom.getEffectiveFrom());
        }

        List<RoutingOperation> operations = routing.getOperations().stream()
                .sorted(Comparator.comparing(RoutingOperation::getSequence))
                .toList();
        Map<UUID, WorkCenter> workCenters = new LinkedHashMap<>();
        operations.forEach(operation -> workCenters.putIfAbsent(
                operation.getWorkCenter().getWorkCenterId(), operation.getWorkCenter()));

        Instant horizon = CapacityBoardService.startOf(
                request.periodEnd().plusDays(1 + HORIZON_DAYS_AFTER_PERIOD), zone);
        List<WorkOrderOperation> loadOperations = calculator.loadOperations(plantId, from, to);
        Map<UUID, List<TimeInterval>> windows = calculator.windows(workCenters.values(),
                CapacityBoardService.spanStart(from, loadOperations),
                CapacityBoardService.spanEnd(horizon, loadOperations), zone);

        List<CapacityPreviewResponse.Operation> scheduled = new ArrayList<>();
        Instant cursor = request.requestedStart();
        for (RoutingOperation operation : operations) {
            CapacityPreviewResponse.Operation placed = place(operation, request.quantity(), cursor,
                    windows.get(operation.getWorkCenter().getWorkCenterId()), to, horizon);
            scheduled.add(placed);
            cursor = placed.proposedEnd();
        }

        Map<UUID, BigDecimal> base = CapacityLoadCalculator.baseLoad(loadOperations, windows, from, to);
        List<CapacityLoadResponse> loads = workCenters.values().stream()
                .map(workCenter -> CapacityLoadCalculator.loadLine(workCenter,
                        windows.get(workCenter.getWorkCenterId()), from, to,
                        base.getOrDefault(workCenter.getWorkCenterId(), BigDecimal.ZERO),
                        sum(scheduled, workCenter, CapacityPreviewResponse.Operation::requiredMinutes),
                        sum(scheduled, workCenter, CapacityPreviewResponse.Operation::inPeriodMinutes)))
                .toList();

        return new CapacityPreviewResponse(plantId, plant.getTimezone(), request.periodStart(), request.periodEnd(),
                request.quantity(), request.requestedStart(), scheduled, loads,
                sourceVersions(routing, bom, workCenters), Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * Walks the work center's working time from {@code cursor} until the operation's required capacity
     * minutes are used up. Windows are cut at the end of the period so every segment is wholly inside
     * or wholly after it — that is what makes {@code inPeriodMinutes} a plain sum.
     */
    private CapacityPreviewResponse.Operation place(RoutingOperation operation, BigDecimal quantity, Instant cursor,
                                                    List<TimeInterval> windows, Instant periodEnd, Instant horizon) {
        WorkCenter workCenter = operation.getWorkCenter();
        BigDecimal units = BigDecimal.valueOf(workCenter.getCapacityUnits());
        BigDecimal required = operation.getSetupMinutes()
                .add(quantity.multiply(operation.getRunMinutesPerUnit()))
                .setScale(CapacityLoadCalculator.SCALE, RoundingMode.HALF_UP);
        BigDecimal remaining = required;
        List<Segment> segments = new ArrayList<>();
        for (TimeInterval window : windows) {
            if (remaining.signum() == 0) {
                break;
            }
            for (TimeInterval piece : splitAt(window.clip(cursor, horizon), periodEnd)) {
                BigDecimal capacity = piece.minutes().multiply(units);
                if (capacity.signum() <= 0) {
                    continue;
                }
                if (remaining.compareTo(capacity) <= 0) {
                    long seconds = remaining.multiply(BigDecimal.valueOf(60))
                            .divide(units, 0, RoundingMode.CEILING).longValueExact();
                    segments.add(new Segment(piece.start(), piece.start().plusSeconds(seconds), remaining));
                    remaining = BigDecimal.ZERO.setScale(CapacityLoadCalculator.SCALE);
                    break;
                }
                segments.add(new Segment(piece.start(), piece.end(), scaled(capacity)));
                remaining = remaining.subtract(capacity);
            }
        }
        if (remaining.signum() > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.SCHEDULING_UNAVAILABLE,
                    "Work center " + workCenter.getCode() + " has no working time to finish operation "
                            + operation.getSequence() + " within " + HORIZON_DAYS_AFTER_PERIOD
                            + " days after the period");
        }
        BigDecimal inPeriod = scaled(segments.stream()
                .filter(segment -> segment.start().isBefore(periodEnd))
                .map(Segment::capacityMinutes)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        Instant start = segments.isEmpty() ? cursor : segments.get(0).start();
        Instant end = segments.isEmpty() ? cursor : segments.get(segments.size() - 1).end();
        return new CapacityPreviewResponse.Operation(
                operation.getRoutingOperationId(),
                operation.getSequence(),
                operation.getName(),
                workCenter.getWorkCenterId(),
                workCenter.getCode(),
                workCenter.getName(),
                workCenter.getCapacityUnits(),
                required,
                inPeriod,
                scaled(required.subtract(inPeriod)),
                start,
                end,
                segments);
    }

    /**
     * Specific errors instead of a quiet zero (FE A28/A29): an inactive or not-yet-effective routing,
     * an operation on another plant or an inactive work center, and a work center with no usable
     * calendar — the last is "cannot schedule", never "schedule in continuous time".
     */
    private void ensureRoutingUsable(RoutingHeader routing, Instant requestedStart, UUID plantId) {
        if (!routing.isActive()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Routing " + routing.getCode() + " is " + routing.getStatus() + "; only an ACTIVE routing can be previewed");
        }
        if (routing.getEffectiveFrom() != null && routing.getEffectiveFrom().isAfter(requestedStart)) {
            throw ExceptionFactory.custom(BusinessErrorCode.EFFECTIVE_DATE_FUTURE,
                    "Routing is effective only from " + routing.getEffectiveFrom());
        }
        if (routing.getOperations().isEmpty()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.DOCUMENT_HAS_NO_LINES, "Routing has no operations");
        }
        for (RoutingOperation operation : routing.getOperations()) {
            WorkCenter workCenter = operation.getWorkCenter();
            if (workCenter == null) {
                throw ExceptionFactory.custom(BusinessErrorCode.SCHEDULING_UNAVAILABLE,
                        "Operation " + operation.getSequence() + " has no work center");
            }
            if (!workCenter.getPlant().getPlantId().equals(plantId)) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                        "Routing runs on work centers of another plant");
            }
            if (!workCenter.isActive()) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                        "Work center " + workCenter.getCode() + " is inactive");
            }
            if (workCenter.getWorkCalendar() == null || !workCenter.getWorkCalendar().isActive()) {
                throw ExceptionFactory.custom(BusinessErrorCode.SCHEDULING_UNAVAILABLE,
                        "Work center " + workCenter.getCode() + " has no ACTIVE work calendar to schedule on");
            }
        }
    }

    private List<TimeInterval> splitAt(TimeInterval interval, Instant boundary) {
        if (interval == null) {
            return List.of();
        }
        if (interval.start().isBefore(boundary) && interval.end().isAfter(boundary)) {
            return List.of(new TimeInterval(interval.start(), boundary), new TimeInterval(boundary, interval.end()));
        }
        return List.of(interval);
    }

    private CapacityPreviewResponse.SourceVersions sourceVersions(RoutingHeader routing, BomHeader bom,
                                                                  Map<UUID, WorkCenter> workCenters) {
        List<SourceRef> centerRefs = workCenters.values().stream()
                .map(workCenter -> new SourceRef(workCenter.getWorkCenterId(), workCenter.getVersion()))
                .toList();
        List<SourceRef> calendarRefs = workCenters.values().stream()
                .map(WorkCenter::getWorkCalendar)
                .distinct()
                .map(calendar -> new SourceRef(calendar.getWorkCalendarId(), calendar.getVersion()))
                .toList();
        return new CapacityPreviewResponse.SourceVersions(
                routing.getRoutingId(), routing.getVersionNumber(), routing.getVersion(),
                bom.getBomId(), bom.getVersionNumber(), bom.getVersion(),
                centerRefs, calendarRefs);
    }

    private static BigDecimal sum(List<CapacityPreviewResponse.Operation> operations, WorkCenter workCenter,
                                  java.util.function.Function<CapacityPreviewResponse.Operation, BigDecimal> figure) {
        return operations.stream()
                .filter(operation -> operation.workCenterId().equals(workCenter.getWorkCenterId()))
                .map(figure)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(CapacityLoadCalculator.SCALE, RoundingMode.HALF_UP);
    }
}
