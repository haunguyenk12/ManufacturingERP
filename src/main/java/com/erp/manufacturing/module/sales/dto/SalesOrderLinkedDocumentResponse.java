package com.erp.manufacturing.module.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A document working for this order (G6a). Today only {@code WORK_ORDER}. {@code quantityForThisOrder}
 * is the part allocated to this order line — never the work order's whole planned quantity.
 */
public record SalesOrderLinkedDocumentResponse(
        String type,
        UUID documentId,
        String documentCode,
        String status,
        UUID salesOrderLineId,
        UUID itemId,
        String uom,
        BigDecimal quantityForThisOrder,
        BigDecimal qcAcceptedQuantity
) {}
