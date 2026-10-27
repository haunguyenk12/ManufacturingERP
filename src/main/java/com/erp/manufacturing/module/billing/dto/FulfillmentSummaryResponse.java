package com.erp.manufacturing.module.billing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /sales-orders/v1/{id}/fulfillment-summary} (G10d, FE R24): QC, delivery, invoice and payment progress side
 * by side. {@code deliveryProgress} is {@code NOT_DELIVERED}, {@code PARTIALLY_DELIVERED} or {@code DELIVERED} ({@code null}
 * while the baseline is unknown); a fully paid order is not closed and not "delivered" by that alone. {@code commercial} is
 * absent without invoice authority.
 */
public record FulfillmentSummaryResponse(
        UUID salesOrderId,
        String orderNo,
        String status,
        Long version,
        boolean deliveryBaselineKnown,
        String deliveryBaselineUnknownReason,
        String deliveryProgress,
        List<FulfillmentLineResponse> lines,
        @JsonInclude(JsonInclude.Include.NON_NULL) FulfillmentMoneyResponse commercial
) {}
