package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * G11a: what a work order was expected to cost, captured once inside its release transaction (FE R05, Q7). Prices are
 * the standard cost of each component and the work center rates in force at that second; a missing one stays
 * {@code null} — release is never blocked for it (D4) and nothing fills it in later (B77, FE R06).
 *
 * <p>The two child lists are bags: never fetch them in the same entity graph (the {@code MultipleBagFetchException}
 * trap of CLAUDE.md §0.27/§0.29/§0.45).
 */
@Entity
@Table(name = "production_cost_baselines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCostBaseline extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "baseline_id", updatable = false, nullable = false)
    private UUID baselineId;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "plant_id", nullable = false, updatable = false)
    private UUID plantId;

    @Column(name = "output_item_id", nullable = false, updatable = false)
    private UUID outputItemId;

    @Column(name = "output_warehouse_id", nullable = false, updatable = false)
    private UUID outputWarehouseId;

    /** On hand of the output item in the output warehouse at release; the isolated pool starts from 0 (D3). */
    @Column(name = "output_opening_quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal outputOpeningQuantity;

    @Column(name = "planned_quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal plannedQuantity;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @Column(name = "captured_by", updatable = false)
    private UUID capturedBy;

    @OneToMany(mappedBy = "baseline", cascade = CascadeType.ALL)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<ProductionCostBaselineMaterial> materials = new ArrayList<>();

    @OneToMany(mappedBy = "baseline", cascade = CascadeType.ALL)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<ProductionCostBaselineOperation> operations = new ArrayList<>();
}
