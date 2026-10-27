package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G11a: one operation of a cost baseline — its active minutes divided by the work center's capacity units (FE Q9) and
 * the rate in force at release. No work center (routing rows before C2-6) ⇒ minutes unknown; no rate ⇒ labor and
 * overhead unknown. A rate of 0 is a known 0 (FE A05).
 */
@Entity
@Table(name = "production_cost_baseline_operations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCostBaselineOperation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "baseline_operation_id", updatable = false, nullable = false)
    private UUID baselineOperationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baseline_id", nullable = false, updatable = false)
    private ProductionCostBaseline baseline;

    @Column(name = "work_order_operation_id", nullable = false, updatable = false)
    private UUID workOrderOperationId;

    @Column(name = "sequence", nullable = false, updatable = false)
    private Integer sequence;

    @Column(name = "name", nullable = false, updatable = false, length = 255)
    private String name;

    @Column(name = "work_center_id", updatable = false)
    private UUID workCenterId;

    @Column(name = "capacity_units", updatable = false)
    private Integer capacityUnits;

    @Column(name = "planned_minutes", updatable = false, precision = 25, scale = 12)
    private BigDecimal plannedMinutes;

    @Column(name = "rate_id", updatable = false)
    private UUID rateId;

    @Column(name = "rate_version", updatable = false)
    private Integer rateVersion;

    @Column(name = "labor_rate", updatable = false, precision = 19, scale = 6)
    private BigDecimal laborRate;

    @Column(name = "overhead_rate", updatable = false, precision = 19, scale = 6)
    private BigDecimal overheadRate;

    @Column(name = "planned_labor", updatable = false, precision = 19, scale = 6)
    private BigDecimal plannedLabor;

    @Column(name = "planned_overhead", updatable = false, precision = 19, scale = 6)
    private BigDecimal plannedOverhead;
}
