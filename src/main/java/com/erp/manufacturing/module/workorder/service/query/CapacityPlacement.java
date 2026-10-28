package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.shift.service.TimeInterval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The one way an operation is laid over working time (CF-5, B269): starting at {@code cursor}, it uses the
 * windows in order with {@code units} people or machines in parallel, so a window of {@code m} minutes holds
 * {@code m × units} capacity minutes. The last, partial window ends after {@code ceil(remaining × 60 / units)}
 * seconds. The plant capacity preview (G5) and the work order scheduler (release and its preview, G8a) both call
 * this — two copies of the loop are what let the preview promise a schedule release never committed.
 */
public final class CapacityPlacement {

    private CapacityPlacement() {
    }

    /** One stretch of the operation, with the capacity minutes it uses (elapsed minutes × units). */
    public record Segment(Instant start, Instant end, BigDecimal capacityMinutes) {}

    /** What was placed, and what is left when the windows ran out first. */
    public record Result(List<Segment> segments, BigDecimal remaining) {}

    /**
     * @param windows   sorted, non-overlapping working time
     * @param horizon   nothing is placed at or after this instant
     * @param boundary  when not {@code null}, a window crossing it is split there, so each segment lies wholly before
     *                  or wholly after it (the plant preview's period end)
     * @param required  capacity minutes to place, scale {@link CapacityLoadCalculator#SCALE}
     */
    public static Result place(List<TimeInterval> windows, Instant cursor, Instant horizon, Instant boundary,
                               BigDecimal required, int units) {
        BigDecimal parallel = BigDecimal.valueOf(units);
        BigDecimal remaining = required;
        List<Segment> segments = new ArrayList<>();
        for (TimeInterval window : windows) {
            if (remaining.signum() == 0) {
                break;
            }
            for (TimeInterval piece : splitAt(window.clip(cursor, horizon), boundary)) {
                BigDecimal capacity = piece.minutes().multiply(parallel);
                if (capacity.signum() <= 0) {
                    continue;
                }
                if (remaining.compareTo(capacity) <= 0) {
                    long seconds = remaining.multiply(BigDecimal.valueOf(60))
                            .divide(parallel, 0, RoundingMode.CEILING).longValueExact();
                    segments.add(new Segment(piece.start(), piece.start().plusSeconds(seconds), remaining));
                    remaining = BigDecimal.ZERO.setScale(CapacityLoadCalculator.SCALE);
                    break;
                }
                segments.add(new Segment(piece.start(), piece.end(), scaled(capacity)));
                remaining = remaining.subtract(capacity);
            }
        }
        return new Result(segments, remaining);
    }

    /** Capacity minutes an operation needs: {@code setup + run × quantity}, scale {@link CapacityLoadCalculator#SCALE}. */
    public static BigDecimal requiredMinutes(BigDecimal setupMinutes, BigDecimal runMinutesPerUnit, BigDecimal quantity) {
        return setupMinutes.add(quantity.multiply(runMinutesPerUnit))
                .setScale(CapacityLoadCalculator.SCALE, RoundingMode.HALF_UP);
    }

    /** Elapsed minutes the operation needs with {@code units} in parallel: {@code required / units}. */
    public static BigDecimal activeElapsedMinutes(BigDecimal required, int units) {
        return required.divide(BigDecimal.valueOf(units), CapacityLoadCalculator.SCALE, RoundingMode.HALF_UP);
    }

    /** Wall time of {@code required} capacity minutes when nothing stops the work: {@code ceil(required × 60 / units)} s. */
    public static long continuousSeconds(BigDecimal required, int units) {
        return required.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(units), 0, RoundingMode.CEILING).longValueExact();
    }

    private static List<TimeInterval> splitAt(TimeInterval interval, Instant boundary) {
        if (interval == null) {
            return List.of();
        }
        if (boundary != null && interval.start().isBefore(boundary) && interval.end().isAfter(boundary)) {
            return List.of(new TimeInterval(interval.start(), boundary), new TimeInterval(boundary, interval.end()));
        }
        return List.of(interval);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(CapacityLoadCalculator.SCALE, RoundingMode.HALF_UP);
    }
}
