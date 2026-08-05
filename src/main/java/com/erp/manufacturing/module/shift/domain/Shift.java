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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Production shift master data (C2-7 Part A, {@code NEXT_PHASE_PLAN.md} §Phần A). Per-plant, same
 * shape as {@link com.erp.manufacturing.module.workcenter.domain.WorkCenter}.
 *
 * <p>A shift is exactly one continuous interval — {@code endTime} before {@code startTime} means
 * the shift crosses midnight (e.g. {@code 22:00}-{@code 06:00}), it does NOT mean an invalid range.
 * {@code breaks} are sub-intervals carved out of that one window; a shift is never a list of
 * disjoint "work intervals" (decision §1.1). See {@code module/shift/CLAUDE.md} for the day
 * attribution convention overnight shifts use once they are placed on a {@link WorkCalendar}.
 */
@Entity
@Table(name = "shifts", indexes = {
        @Index(name = "idx_shifts_plant_id", columnList = "plant_id"),
        @Index(name = "idx_shifts_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shift extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "shift_id", updatable = false, nullable = false)
    private UUID shiftId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime ASC")
    @Builder.Default
    private List<ShiftBreak> breaks = new ArrayList<>();

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
