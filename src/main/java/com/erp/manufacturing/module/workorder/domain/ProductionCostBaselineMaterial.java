package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/** G11a: one component requirement of a cost baseline, priced at its standard cost at release (D1). */
@Entity
@Table(name = "production_cost_baseline_materials")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCostBaselineMaterial extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "baseline_material_id", updatable = false, nullable = false)
    private UUID baselineMaterialId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baseline_id", nullable = false, updatable = false)
    private ProductionCostBaseline baseline;

    @Column(name = "line_no", nullable = false, updatable = false)
    private Integer lineNo;

    @Column(name = "component_line_id", nullable = false, updatable = false)
    private UUID componentLineId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "warehouse_id", updatable = false)
    private UUID warehouseId;

    @Column(name = "uom", nullable = false, updatable = false, length = 50)
    private String uom;

    @Column(name = "planned_quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal plannedQuantity;

    /** {@code null}: no standard cost configured at release. */
    @Column(name = "unit_cost", updatable = false, precision = 25, scale = 12)
    private BigDecimal unitCost;

    @Column(name = "planned_amount", updatable = false, precision = 19, scale = 6)
    private BigDecimal plannedAmount;
}
