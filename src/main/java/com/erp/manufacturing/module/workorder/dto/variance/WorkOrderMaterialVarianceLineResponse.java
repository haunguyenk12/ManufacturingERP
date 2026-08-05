package com.erp.manufacturing.module.workorder.dto.variance;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code usageVarianceCost} = {@code varianceQuantity} times the component item's own fully-loaded
 * standard unit cost (rolled up through its BOM if it is itself manufactured) — the dollar impact of
 * over/under-issuing this component, independent of whether the component's price ever changed
 * (P3 ships Material Usage Variance only, not Price Variance — see NEXT_PHASE_PLAN.md "P3" decision #2).
 */
public record WorkOrderMaterialVarianceLineResponse(
        UUID componentLineId,
        UUID componentItemId,
        String componentItemCode,
        String componentItemName,
        BigDecimal plannedQuantity,
        BigDecimal actualIssuedQuantity,
        BigDecimal varianceQuantity,
        String status,
        BigDecimal usageVarianceCost
) {}
