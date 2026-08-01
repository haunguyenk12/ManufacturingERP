package com.erp.manufacturing.module.planning.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "mrp_requirement_lines", indexes = {
        @Index(name = "idx_mrp_requirement_lines_run_item_status", columnList = "mrp_run_id, item_id, requirement_status"),
        @Index(name = "idx_mrp_requirement_lines_parent_id", columnList = "parent_requirement_line_id"),
        @Index(name = "idx_mrp_requirement_lines_due_date", columnList = "due_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MrpRequirementLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mrp_requirement_line_id", updatable = false, nullable = false)
    private UUID mrpRequirementLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mrp_run_id", nullable = false)
    private MrpRun mrpRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_requirement_line_id")
    private MrpRequirementLine parentRequirementLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_demand_id")
    private PlanningDemand sourceDemand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "requirement_level", nullable = false)
    private Integer requirementLevel;

    @Column(name = "gross_required_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal grossRequiredQuantity = BigDecimal.ZERO;

    @Column(name = "available_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal availableQuantity = BigDecimal.ZERO;

    @Column(name = "reserved_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "open_supply_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal openSupplyQuantity = BigDecimal.ZERO;

    @Column(name = "safety_stock_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal safetyStockQuantity = BigDecimal.ZERO;

    /**
     * Coverage still unclaimed when this line was netted (spec §2.4):
     * {@code max(0, availableQuantity + openSupplyQuantity - coverage consumed by earlier lines)}.
     *
     * <p>Cannot be derived from the other columns. The subtrahend is the coverage already eaten by
     * <em>earlier</em> requirement lines for the same (item, warehouse), which lives only inside one
     * MRP calculation; {@code availableQuantity + openSupplyQuantity} therefore over-reports as soon
     * as an item appears on more than one line. The frontend checks
     * {@code netRequired = max(0, gross + safetyStock - projectedAvailable)}, so this must be the
     * exact figure the netting used — including its clamp at zero (invariant B77).
     *
     * <p>Nullable, with no default: {@code null} means "run executed before V40", not "nothing
     * available". V40 deliberately does not backfill.
     */
    @Column(name = "projected_available_quantity", precision = 19, scale = 6)
    private BigDecimal projectedAvailableQuantity;

    @Column(name = "net_required_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal netRequiredQuantity = BigDecimal.ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_status", nullable = false, length = 20)
    private MrpRequirementStatus requirementStatus;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "setting_source", nullable = false, length = 30)
    @Builder.Default
    private PlanningSettingSource settingSource = PlanningSettingSource.SYSTEM_DEFAULT;

    @Column(name = "excluded_lot_count", nullable = false)
    @Builder.Default
    private Integer excludedLotCount = 0;
}
