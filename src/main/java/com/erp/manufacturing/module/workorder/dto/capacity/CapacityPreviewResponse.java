package com.erp.manufacturing.module.workorder.dto.capacity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Result of a capacity preview (G5). An estimate, not a commitment: operations run one after another
 * by sequence on infinite capacity (FE Q17), so {@code loads} may show more allocated than available —
 * that is the warning, not an error.
 */
public record CapacityPreviewResponse(
        UUID plantId,
        String timezone,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal quantity,
        Instant requestedStart,
        List<Operation> operations,
        List<CapacityLoadResponse> loads,
        SourceVersions sourceVersions,
        Instant generatedAt
) {

    /**
     * @param requiredMinutes   setup + quantity × run, in capacity minutes
     * @param inPeriodMinutes   part of it scheduled before the end of the period
     * @param afterPeriodMinutes the rest
     */
    public record Operation(
            UUID routingOperationId,
            Integer sequence,
            String name,
            UUID workCenterId,
            String workCenterCode,
            String workCenterName,
            Integer capacityUnits,
            BigDecimal requiredMinutes,
            BigDecimal inPeriodMinutes,
            BigDecimal afterPeriodMinutes,
            Instant proposedStart,
            Instant proposedEnd,
            List<Segment> segments
    ) {}

    /** One stretch of working time the operation occupies; {@code capacityMinutes} = elapsed × capacity units. */
    public record Segment(Instant start, Instant end, BigDecimal capacityMinutes) {}

    /** What the preview read, so the caller can tell when a source changed since (not a commit token). */
    public record SourceVersions(
            UUID routingId,
            Integer routingVersionNumber,
            Long routingLockVersion,
            UUID bomId,
            Integer bomVersionNumber,
            Long bomLockVersion,
            List<SourceRef> workCenters,
            List<SourceRef> workCalendars
    ) {}

    public record SourceRef(UUID id, Long lockVersion) {}
}
