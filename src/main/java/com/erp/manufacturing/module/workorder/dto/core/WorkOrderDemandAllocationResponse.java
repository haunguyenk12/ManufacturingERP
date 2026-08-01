package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One sales order line this work order's output is earmarked for (spec §2.4). */
public record WorkOrderDemandAllocationResponse(
        UUID allocationId,
        UUID salesOrderLineId,
        String salesOrderCode,
        BigDecimal allocatedQuantity,
        BigDecimal fulfilledQuantity,
        String uom,
        LocalDate dueDate
) {}
