package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A POSTED invoice the payment can be allocated to — the minimal balance only (FE Q30): amount, paid by POSTED payments,
 * outstanding, claimed by this draft and by other drafts, and what can still be allocated.
 */
public record PaymentInvoiceCandidateResponse(
        UUID invoiceId,
        String code,
        LocalDate documentDate,
        LocalDate dueDate,
        UUID salesOrderId,
        String orderNo,
        Long version,
        String currency,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        BigDecimal ownDraftClaim,
        BigDecimal otherDraftClaim,
        BigDecimal availableToAllocate
) {}
