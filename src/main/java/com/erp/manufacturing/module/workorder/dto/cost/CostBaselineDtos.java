package com.erp.manufacturing.module.workorder.dto.cost;

import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.ActorRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** G11a: the cost baseline captured at release (FE C5, §3.3 {@code baseline.*}). */
public final class CostBaselineDtos {

    private CostBaselineDtos() {
    }

    /** Planned or actual cost split; a component is {@code null} when unknown, the total too (FE §3.5 #6). */
    public record CostTotals(BigDecimal material, BigDecimal labor, BigDecimal overhead, BigDecimal total) {
    }

    public record BaselineMaterial(UUID componentLineId, Integer lineNo, UUID itemId, UUID warehouseId, String uom,
                                   BigDecimal plannedQuantity, BigDecimal unitCost, BigDecimal plannedAmount) {
    }

    public record BaselineOperation(UUID operationId, Integer sequence, String name, UUID workCenterId,
                                    Integer capacityUnits, BigDecimal plannedMinutes, Integer rateVersion,
                                    BigDecimal laborRate, BigDecimal overheadRate, BigDecimal plannedLabor,
                                    BigDecimal plannedOverhead) {
    }

    public record Baseline(UUID baselineId, Instant capturedAt, ActorRef capturedBy, UUID outputWarehouseId,
                           BigDecimal outputOpeningQuantity, BigDecimal plannedQuantity, String currency,
                           CostTotals planned, List<BaselineMaterial> materials, List<BaselineOperation> operations) {
    }

    /** C5 result: {@code baseline} is {@code null} for a work order released before G11 (or not released yet). */
    public record BaselineView(UUID workOrderId, Baseline baseline, List<String> unknownReasons) {
    }
}
