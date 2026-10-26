package com.erp.manufacturing.module.billing.dto;

import com.erp.manufacturing.common.response.PageResult;

import java.util.UUID;

/**
 * The order an invoice would be for, and its billable delivery lines. {@code invoiceable = false} names why in
 * {@code blockedReason}: {@code ORDER_STATUS}, {@code CUSTOMER_MISSING} or {@code CUSTOMER_INACTIVE}.
 */
public record SalesInvoiceCandidatesResponse(
        UUID salesOrderId,
        String orderNo,
        String status,
        Long version,
        UUID customerId,
        String customerName,
        String currency,
        String pricingVersion,
        boolean invoiceable,
        String blockedReason,
        PageResult<SalesInvoiceCandidateLineResponse> lines
) {}
