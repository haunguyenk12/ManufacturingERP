package com.erp.manufacturing.module.shift.service;

import java.time.LocalTime;

/**
 * Pure time-interval math shared by break validation ({@code ShiftService}) and net working window
 * computation ({@code WorkingWindowCalculator}) — the "quy ước xử lý qua đêm dùng chung" the plan
 * calls for (NEXT_PHASE_PLAN.md C2-7 §Phần A #3). No Spring/DB dependency, so it is directly unit
 * testable.
 *
 * <p>An overnight interval is one where {@code end} is before {@code start} in clock time (e.g.
 * shift {@code 22:00}-{@code 06:00}). All durations here are measured "circularly": always
 * non-negative, wrapping through midnight when {@code end} is before {@code start}.
 */
final class ShiftTimeWindow {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private ShiftTimeWindow() {
    }

    /** Minutes from {@code start} to {@code end}, wrapping through midnight if {@code end < start}. */
    static int durationMinutes(LocalTime start, LocalTime end) {
        int raw = (end.toSecondOfDay() - start.toSecondOfDay()) / 60;
        return raw >= 0 ? raw : raw + MINUTES_PER_DAY;
    }

    /**
     * Whether the interval {@code [innerStart, innerEnd)} lies entirely inside
     * {@code [outerStart, outerEnd)}, both measured circularly from {@code outerStart} so an
     * overnight outer interval (and an inner interval that itself crosses midnight, e.g. a break
     * that spans the shift's midnight boundary) are handled without a separate branch.
     */
    static boolean containsInterval(LocalTime outerStart, LocalTime outerEnd, LocalTime innerStart, LocalTime innerEnd) {
        int outerDuration = durationMinutes(outerStart, outerEnd);
        int innerStartOffset = durationMinutes(outerStart, innerStart);
        int innerDuration = durationMinutes(innerStart, innerEnd);
        return innerDuration > 0 && innerStartOffset + innerDuration <= outerDuration;
    }
}
