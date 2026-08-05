package com.erp.manufacturing.module.costing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Standard cost master data for one item (P3, {@code NEXT_PHASE_PLAN.md} §"P3 — Costing Engine").
 *
 * <p>One row per item (upsert, no history/{@code effectiveDate} versioning) — company-scoped like
 * {@link com.erp.manufacturing.module.bom.domain.BomHeader}, not per-plant and not global.
 *
 * <p>{@code materialCost} is only meaningful for items <b>without</b> an {@code ACTIVE} BOM
 * (leaf/purchased items). For a manufactured item, material cost is derived from the BOM roll-up
 * instead ({@link com.erp.manufacturing.module.costing.service.CostingService}) and this column is
 * simply unused — there is no schema-level distinction, the roll-up picks the source based on
 * whether an {@code ACTIVE} BOM exists for the item.
 */
@Entity
@Table(name = "item_standard_costs", indexes = {
        @Index(name = "idx_item_standard_costs_company_id", columnList = "company_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemStandardCost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_standard_cost_id", updatable = false, nullable = false)
    private UUID itemStandardCostId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "material_cost", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal materialCost = BigDecimal.ZERO;

    @Column(name = "labor_cost", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "overhead_cost", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal overheadCost = BigDecimal.ZERO;
}
