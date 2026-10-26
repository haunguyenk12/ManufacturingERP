package com.erp.manufacturing.module.delivery.dto;

import com.erp.manufacturing.common.response.PageResult;

import java.util.UUID;

/**
 * The order a delivery would be for, and its lines. {@code deliverable = false} names why in {@code blockedReason}:
 * {@code ORDER_STATUS}, {@code BASELINE_UNKNOWN} (confirmed before V86), {@code CUSTOMER_MISSING} or
 * {@code CUSTOMER_INACTIVE}.
 */
public record DeliveryCandidatesResponse(
        UUID salesOrderId,
        String orderNo,
        String status,
        Long version,
        UUID customerId,
        String customerName,
        boolean deliverable,
        String blockedReason,
        PageResult<DeliveryCandidateLineResponse> lines
) {}
