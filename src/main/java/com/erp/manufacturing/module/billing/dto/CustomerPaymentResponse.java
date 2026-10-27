package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** G10d: a customer payment with its allocations. {@code allowedActions} is a UX hint; every command checks again. */
public record CustomerPaymentResponse(
        UUID paymentId,
        String code,
        String type,
        String status,
        Long version,
        UUID companyId,
        UUID plantId,
        UUID customerId,
        String customerName,
        LocalDate documentDate,
        String method,
        String reference,
        String note,
        String currency,
        BigDecimal amount,
        List<CustomerPaymentAllocationResponse> allocations,
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
    public static final String TYPE = "PAYMENT";
}
