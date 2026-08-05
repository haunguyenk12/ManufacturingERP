package com.erp.manufacturing.module.shift.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.util.UUID;

/**
 * Assigns one {@link Shift} to one weekday of a {@link WorkCalendar}. A weekday MAY carry more
 * than one shift (e.g. a day shift and a night shift both running on Monday) — the uniqueness
 * constraint is on {@code (work_calendar_id, weekday, shift_id)}, not on {@code (work_calendar_id,
 * weekday)}.
 */
@Entity
@Table(name = "work_calendar_weekly_shifts", indexes = {
        @Index(name = "idx_work_calendar_weekly_shifts_calendar_id", columnList = "work_calendar_id"),
        @Index(name = "idx_work_calendar_weekly_shifts_shift_id", columnList = "shift_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarWeeklyShift extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "work_calendar_weekly_shift_id", updatable = false, nullable = false)
    private UUID workCalendarWeeklyShiftId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_calendar_id", nullable = false)
    private WorkCalendar workCalendar;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekday", nullable = false, length = 10)
    private Weekday weekday;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;
}
