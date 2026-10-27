package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One allocation with the minimal invoice it pays (FE Q30: no order prices, no invoice lines). */
public record CustomerPaymentAllocationResponse(
        UUID allocationId,
        Integer lineNo,
        UUID invoiceId,
        String invoiceCode,
        LocalDate invoiceDate,
        UUID salesOrderId,
        String orderNo,
        BigDecimal amount
) {}
