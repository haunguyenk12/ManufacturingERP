package com.erp.manufacturing.module.sales.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What another module is allowed to know about a sales order line it holds an allocation against
 * (spec §2.4). Deliberately a flat value object rather than the {@code SalesOrderLine} entity, so
 * {@code workorder} can order and size allocations without gaining a compile-time dependency on
 * the sales domain model — rule {@code C7}.
 *
 * @param dueDate together with {@code lineNo}, the tie-break that makes fulfilment ordering
 *                deterministic instead of "whatever order the database returned".
 */
public record SalesOrderAllocationTarget(
        UUID salesOrderLineId,
        UUID salesOrderId,
        String salesOrderCode,
        Integer lineNo,
        LocalDate dueDate,
        String uom,
        BigDecimal orderedQuantity,
        BigDecimal fulfilledQuantity
) {

    public BigDecimal openQuantity() {
        return orderedQuantity.subtract(fulfilledQuantity);
    }
}
