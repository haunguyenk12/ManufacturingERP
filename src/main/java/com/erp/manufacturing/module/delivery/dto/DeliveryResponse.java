package com.erp.manufacturing.module.delivery.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * G10b: a delivery with its lines. No money anywhere (FE §1.3: delivery authority does not see prices).
 * {@code valuationStatus} is always {@code PENDING_COSTING}: stock carries no cost yet (D7), and the sales price is
 * never used in its place (FE R10). {@code allowedActions} is a UX hint; the server checks again on every command.
 */
public record DeliveryResponse(
        UUID deliveryId,
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
        String note,
        BigDecimal totalQuantity,
        List<DeliveryLineResponse> lines,
        String valuationStatus,
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
    public static final String TYPE = "DELIVERY";
    public static final String PENDING_COSTING = "PENDING_COSTING";
}
