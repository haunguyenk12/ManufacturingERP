package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Running total of actual cost incurred by a work order (P3, {@code NEXT_PHASE_PLAN.md}
 * "P3 — Costing Engine"). One row per work order, created on first accumulation — a work order that
 * has issued nothing and reported nothing simply has no row, which
 * {@code WorkOrderVarianceService} reads as all-zero actual cost.
 *
 * <p>{@code materialCostAccumulated} grows in {@code MaterialIssueService.postNew} — each issued
 * component line's fully-loaded standard unit cost (material + labor + overhead, rolled up through
 * its own BOM if it is itself manufactured) times the quantity issued.
 *
 * <p>{@code laborCostAccumulated}/{@code overheadCostAccumulated} grow in
 * {@code ProductionExecutionService.reportNew} — the work order's own product item's flat
 * labor/overhead rate times the good quantity reported (scrap/rework earn nothing, they are not
 * sellable output).
 */
@Entity
@Table(name = "work_order_cost_accumulators")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderCostAccumulator extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "work_order_cost_accumulator_id", updatable = false, nullable = false)
    private UUID workOrderCostAccumulatorId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(name = "material_cost_accumulated", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal materialCostAccumulated = BigDecimal.ZERO;

    @Column(name = "labor_cost_accumulated", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal laborCostAccumulated = BigDecimal.ZERO;

    @Column(name = "overhead_cost_accumulated", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal overheadCostAccumulated = BigDecimal.ZERO;

    public void addMaterialCost(BigDecimal amount) {
        materialCostAccumulated = materialCostAccumulated.add(amount);
    }

    public void addLaborAndOverheadCost(BigDecimal laborAmount, BigDecimal overheadAmount) {
        laborCostAccumulated = laborCostAccumulated.add(laborAmount);
        overheadCostAccumulated = overheadCostAccumulated.add(overheadAmount);
    }
}
