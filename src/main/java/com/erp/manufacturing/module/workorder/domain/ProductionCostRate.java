package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G11a: one saved hourly rate of a work center, in VND per hour of operating time (FE Q2 — the work center, not a
 * person). Rows are never updated: saving a rate writes version n+1, so the rows are the history (FE R04) and a
 * baseline captured earlier keeps pointing at the row it read (invariant B256).
 */
@Entity
@Table(name = "production_cost_rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCostRate extends BaseEntity {

    public static final String CURRENCY = "VND";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rate_id", updatable = false, nullable = false)
    private UUID rateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_center_id", nullable = false, updatable = false)
    private WorkCenter workCenter;

    @Column(name = "plant_id", nullable = false, updatable = false)
    private UUID plantId;

    @Column(name = "rate_version", nullable = false, updatable = false)
    private Integer rateVersion;

    @Column(name = "labor_rate", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal laborRate;

    @Column(name = "overhead_rate", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal overheadRate;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    @Builder.Default
    private String currency = CURRENCY;

    @Column(name = "reason", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String reason;
}
