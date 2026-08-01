package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the shop-floor "which work order am I reporting against?" screen (spec §5.1).
 *
 * <p>Deliberately a lean read model rather than {@code WorkOrderResponse}: that record carries 36
 * fields and its list path also resolves demand allocations, none of which this screen shows. What
 * the operator needs is the work order's identity, what it makes, and how much is left to report.
 *
 * <p>{@code remainingQuantity} is {@code plannedQuantity - actualGoodQuantity} — the ceiling spec
 * §5.1 imposes on cumulative good. It is always greater than zero here, because a work order that
 * reached its plan is not a candidate at all (invariant B75).
 */
public record ProductionExecutionCandidateResponse(
        UUID workOrderId,
        String workOrderNo,
        String status,
        UUID productItemId,
        String itemSku,
        String itemName,
        /**
         * Unit of measure of the output item — the fourth cell of the §5.2 "Context" panel
         * ({@code outputItemSku, outputItemName, uom, plannedQuantity}). Added in F9: F8 filled the
         * other three and missed this one because it worked from its own list of seven DTOs instead
         * of from the spec table.
         */
        String uom,
        BigDecimal plannedQuantity,
        BigDecimal actualGoodQuantity,
        BigDecimal remainingQuantity,
        Instant plannedEndAt
) {}
