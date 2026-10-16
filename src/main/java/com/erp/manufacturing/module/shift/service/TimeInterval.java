package com.erp.manufacturing.module.shift.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * A half-open slice {@code [start, end)} of absolute time (G5). The zone-aware counterpart of
 * {@link WorkingInterval}: capacity arithmetic works on instants so that "the part of a shift that
 * falls inside a period" is a plain intersection, whatever day the shift started on.
 */
public record TimeInterval(Instant start, Instant end) {

    /** Exact minutes, fractional when the interval does not end on a whole minute. */
    public BigDecimal minutes() {
        return BigDecimal.valueOf(Duration.between(start, end).toSeconds())
                .divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }

    /** The overlap with {@code [from, to)}, or {@code null} when there is none. */
    public TimeInterval clip(Instant from, Instant to) {
        Instant clippedStart = start.isBefore(from) ? from : start;
        Instant clippedEnd = end.isAfter(to) ? to : end;
        return clippedStart.isBefore(clippedEnd) ? new TimeInterval(clippedStart, clippedEnd) : null;
    }
}
