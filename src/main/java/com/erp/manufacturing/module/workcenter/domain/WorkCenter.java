package com.erp.manufacturing.module.workcenter.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
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
 * Production work center master data (C2-6 Part A, {@code NEXT_PHASE_PLAN.md} §1.1).
 *
 * <p>Per-plant, not company-level — matches Warehouse and how Routing is actually used, and lets
 * the future Capacity Board (C2-8) filter by plant through this entity directly instead of an
 * indirection through Work Order. {@code status} reuses {@link OrganizationStatus} (the same enum
 * Warehouse/Company/Plant use) rather than a new {@code WorkCenterStatus}: same ACTIVE/INACTIVE
 * semantics, no revision concept to justify a distinct type.
 */
@Entity
@Table(name = "work_centers", indexes = {
        @Index(name = "idx_work_centers_plant_id", columnList = "plant_id"),
        @Index(name = "idx_work_centers_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkCenter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "work_center_id", updatable = false, nullable = false)
    private UUID workCenterId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_unit_type", nullable = false, length = 20)
    private CapacityUnitType capacityUnitType;

    @Column(name = "capacity_units", nullable = false)
    private Integer capacityUnits;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

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
