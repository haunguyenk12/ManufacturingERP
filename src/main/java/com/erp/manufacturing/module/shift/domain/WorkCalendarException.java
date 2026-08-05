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

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single calendar date marked as non-working (holiday, Tet), overriding the weekly pattern to
 * zero working hours for that date. Deliberately one-directional (decision §1.2,
 * {@code NEXT_PHASE_PLAN.md} C2-7 §1) — there is no "type" column; a row here always means
 * NON_WORKING. A "working override" (make-up day) is a distinct future enum value, not part of
 * this phase.
 */
@Entity
@Table(name = "work_calendar_exceptions", indexes = {
        @Index(name = "idx_work_calendar_exceptions_calendar_id", columnList = "work_calendar_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarException extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "work_calendar_exception_id", updatable = false, nullable = false)
    private UUID workCalendarExceptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_calendar_id", nullable = false)
    private WorkCalendar workCalendar;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Column(name = "reason", length = 255)
    private String reason;
}
