package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One invoice line with the source delivery line, the locked price and the base it was priced on. */
public record SalesInvoiceLineResponse(
        UUID invoiceLineId,
        Integer lineNo,
        UUID deliveryLineId,
        UUID deliveryId,
        String deliveryCode,
        UUID salesOrderLineId,
        Integer salesOrderLineNo,
        UUID itemId,
        String itemSku,
        String itemName,
        String uom,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        BigDecimal billedQuantityBefore,
        BigDecimal billedAmountBefore
) {}
