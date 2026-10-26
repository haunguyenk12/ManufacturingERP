package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * G10c: an invoice with its lines. Only invoice authority reads it, so the money is always there. A DRAFT's amount is
 * a preview, not a receivable (FE §3.6). {@code allowedActions} is a UX hint; every command checks again.
 */
public record SalesInvoiceResponse(
        UUID invoiceId,
        String code,
        String type,
        String status,
        Long version,
        UUID companyId,
        UUID plantId,
        UUID salesOrderId,
        String orderNo,
        UUID customerId,
        String customerName,
        LocalDate documentDate,
        LocalDate dueDate,
        String note,
        String currency,
        BigDecimal amount,
        String billingSnapshotVersion,
        List<SalesInvoiceLineResponse> lines,
        UUID createdBy,
        String createdByUsername,
        Instant createdAt,
        UUID postedBy,
        String postedByUsername,
        Instant postedAt,
        UUID cancelledBy,
        String cancelledByUsername,
        Instant cancelledAt,
        String cancelReason,
        List<String> allowedActions
) {
    public static final String TYPE = "INVOICE";
    public static final String VND = "VND";
}
