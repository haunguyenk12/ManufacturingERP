package com.erp.manufacturing.module.shift.service;

import java.time.LocalDateTime;

/**
 * A concrete slice of actual working time (breaks already subtracted). {@code end} may fall on the
 * calendar date after {@code start} for an overnight shift (NEXT_PHASE_PLAN.md C2-7 §Phần D #2).
 */
public record WorkingInterval(LocalDateTime start, LocalDateTime end) {
}
