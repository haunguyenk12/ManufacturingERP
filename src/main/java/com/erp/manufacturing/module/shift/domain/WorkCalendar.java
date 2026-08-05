package com.erp.manufacturing.module.shift.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Weekly shift pattern + one-off non-working exceptions for a plant (C2-7 Part B,
 * {@code NEXT_PHASE_PLAN.md} §Phần B). One work calendar can be referenced by any number of
 * {@link com.erp.manufacturing.module.workcenter.domain.WorkCenter} rows (decision §1.3: no
 * schedule-history/versioning concept in this phase — one calendar is current at a time).
 */
@Entity
@Table(name = "work_calendars", indexes = {
        @Index(name = "idx_work_calendars_plant_id", columnList = "plant_id"),
        @Index(name = "idx_work_calendars_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendar extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "work_calendar_id", updatable = false, nullable = false)
    private UUID workCalendarId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** {@code null} means no expiry. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    @OneToMany(mappedBy = "workCalendar", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkCalendarWeeklyShift> weeklyShifts = new ArrayList<>();

    @OneToMany(mappedBy = "workCalendar", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkCalendarException> exceptions = new ArrayList<>();

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    public void activate() {
        status = OrganizationStatus.ACTIVE;
    }

    public void deactivate() {
        status = OrganizationStatus.INACTIVE;
    }
}
