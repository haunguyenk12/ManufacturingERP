package com.erp.manufacturing.module.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One selectable demand line for a planning run, shaped after spec §2.4 ("Demand" rows):
 * {@code salesOrderCode, salesOrderLineId, itemSku, itemName, uom, quantity, dueDate}.
 *
 * <p>{@code quantity} is the <b>open</b> quantity ({@code orderedQuantity − fulfilledQuantity}),
 * not the ordered quantity — spec §2.1 requires demand to already net out what is fulfilled.
 *
 * <p>Note the naming split: the entity/CRUD contract calls the document number {@code orderNo}
 * (per {@code NEXT_PHASE_PLAN.md §1}) while this planning-facing view calls it
 * {@code salesOrderCode} (per spec §2.4). Both names are fixed by their own source; see
 * {@code module/sales/CLAUDE.md}.
 *
 * <p>{@code planningDemandId} is what {@code POST /planning-runs} expects in {@code demandLineIds}
 * (added in D9/D10, debt #21). Without it this screen listed lines the planner could pick but gave no
 * id the run could resolve, so the two endpoints could not be connected at all. It is never
 * {@code null} here: a line only appears in this list while its demand is still {@code OPEN}.
 */
public record PlanningDemandLineResponse(
        UUID salesOrderId,
        String salesOrderCode,
        UUID salesOrderLineId,
        UUID planningDemandId,
        Integer lineNo,
        UUID itemId,
        String itemSku,
        String itemName,
        String uom,
        BigDecimal quantity,
        LocalDate dueDate
) {}
