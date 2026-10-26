package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of a POSTED delivery of the order: delivered, billed by POSTED invoices, claimed by this draft and by other
 * drafts, and what can still be billed ({@code delivered − posted − other drafts}). {@code unitPrice} is the order
 * line's locked price, {@code null} while unknown ({@code priceKnown = false}).
 */
public record SalesInvoiceCandidateLineResponse(
        UUID deliveryLineId,
        UUID deliveryId,
        String deliveryCode,
        LocalDate deliveryDate,
        UUID salesOrderLineId,
        Integer salesOrderLineNo,
        UUID itemId,
        String itemSku,
        String itemName,
        String uom,
        BigDecimal deliveredQuantity,
        BigDecimal postedInvoicedQuantity,
        BigDecimal ownDraftClaim,
        BigDecimal otherDraftClaim,
        BigDecimal unclaimedQuantity,
        BigDecimal unitPrice,
        boolean priceKnown
) {}
