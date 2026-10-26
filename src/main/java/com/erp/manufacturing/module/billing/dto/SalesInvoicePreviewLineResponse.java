package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One priced line: {@code amount = round((billedQuantityBefore + quantity) × unitPrice) − billedAmountBefore}. */
public record SalesInvoicePreviewLineResponse(
        UUID deliveryLineId,
        UUID salesOrderLineId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        BigDecimal billedQuantityBefore,
        BigDecimal billedAmountBefore
) {}
