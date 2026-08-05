package com.erp.manufacturing.module.costing.service;

import java.math.BigDecimal;

/**
 * Per-unit standard cost of an item, broken down by component.
 *
 * <p>{@code materialCost} is either the item's own {@code ItemStandardCost.materialCost} (leaf item,
 * no {@code ACTIVE} BOM) or the BOM roll-up of its components' {@link #totalCost()} (manufactured
 * item). {@code laborCost}/{@code overheadCost} always come straight from the item's own
 * {@code ItemStandardCost} row (flat manual rate — never rolled up from the BOM, see
 * {@code NEXT_PHASE_PLAN.md} P3 decision #1).
 */
public record StandardCostBreakdown(BigDecimal materialCost, BigDecimal laborCost, BigDecimal overheadCost) {

    public BigDecimal totalCost() {
        return materialCost.add(laborCost).add(overheadCost);
    }
}
