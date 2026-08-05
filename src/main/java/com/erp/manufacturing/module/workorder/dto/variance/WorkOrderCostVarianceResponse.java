package com.erp.manufacturing.module.workorder.dto.variance;

import java.math.BigDecimal;

/**
 * {@code standard*} = {@code CostingService.calculateStandardCost} for the work order's product item
 * (rolled up through the BOM if it has one) times {@code plannedQuantity} — what it should have cost.
 * {@code actual*} = the work order's {@code WorkOrderCostAccumulator} running total (all zero if the
 * work order has issued nothing and reported nothing yet) — what it has actually cost so far.
 */
public record WorkOrderCostVarianceResponse(
        BigDecimal standardMaterialCost,
        BigDecimal standardLaborCost,
        BigDecimal standardOverheadCost,
        BigDecimal standardTotalCost,
        BigDecimal actualMaterialCost,
        BigDecimal actualLaborCost,
        BigDecimal actualOverheadCost,
        BigDecimal actualTotalCost,
        BigDecimal totalCostVariance
) {}
