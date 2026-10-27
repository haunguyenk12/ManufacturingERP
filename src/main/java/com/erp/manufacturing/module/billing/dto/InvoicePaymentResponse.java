package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A payment allocated to one invoice: only the part allocated to it, not the payment's whole amount. */
public record InvoicePaymentResponse(
        UUID paymentId,
        String code,
        String status,
        LocalDate documentDate,
        String method,
        BigDecimal allocatedAmount,
        Instant postedAt
) {}
