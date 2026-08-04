package com.erp.manufacturing.module.uom.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Unit of measure master data (C2-3, spec {@code BACKEND_CAPSTONE2_API_GAPS.md §3.1}).
 *
 * <p>Deliberately global — no {@code company_id} — see {@code NEXT_PHASE_PLAN.md} (C2-3) "Thiết kế
 * đã chốt" #1. {@code code} is immutable after creation by convention: {@link UomStatus} enforces it
 * simply by never exposing a setter path for it outside {@code UomService.create}.
 */
@Entity
@Table(name = "uoms", indexes = {
        @Index(name = "idx_uoms_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Uom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uom_id", updatable = false, nullable = false)
    private UUID uomId;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UomStatus status = UomStatus.ACTIVE;

    public boolean isActive() {
        return status == UomStatus.ACTIVE;
    }

    public void activate() {
        status = UomStatus.ACTIVE;
    }

    public void deactivate() {
        status = UomStatus.INACTIVE;
    }
}
