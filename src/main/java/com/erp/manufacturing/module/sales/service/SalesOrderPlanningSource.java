package com.erp.manufacturing.module.sales.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What planning reads about one sales order line (G6b). {@code uncoveredQuantity} is the same figure
 * the sales order detail shows (B172): {@code max(0, ordered - qcAccepted - heldStock - workOrderCovered)}.
 */
public record SalesOrderPlanningSource(
        UUID salesOrderLineId,
        UUID salesOrderId,
        String salesOrderNo,
        Integer lineNo,
        UUID companyId,
        UUID plantId,
        String orderStatus,
        boolean planningEligible,
        UUID itemId,
        String uom,
        BigDecimal orderedQuantity,
        BigDecimal qcAcceptedQuantity,
        BigDecimal heldStockQuantity,
        BigDecimal workOrderCoveredQuantity,
        BigDecimal uncoveredQuantity,
        LocalDate dueDate
) {}
