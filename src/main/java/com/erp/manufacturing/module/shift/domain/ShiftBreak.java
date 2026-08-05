package com.erp.manufacturing.module.shift.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A break carved out of the parent {@link Shift}'s single continuous interval. Whether it falls
 * inside the shift window (accounting for an overnight shift) is validated by
 * {@code ShiftService}/{@code ShiftTimeWindow}, not by a DB constraint — the check depends on the
 * parent shift's start/end time, which isn't expressible as a column check.
 */
@Entity
@Table(name = "shift_breaks", indexes = {
        @Index(name = "idx_shift_breaks_shift_id", columnList = "shift_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftBreak extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "shift_break_id", updatable = false, nullable = false)
    private UUID shiftBreakId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
