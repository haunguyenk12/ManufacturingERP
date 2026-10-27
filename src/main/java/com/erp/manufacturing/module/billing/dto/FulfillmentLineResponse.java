package com.erp.manufacturing.module.billing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One order line's progress (FE R24): each figure is its own fact — QC accepted, delivered, invoiced — none implies the
 * others. Delivery and invoice figures are {@code null} while the order's delivery baseline is unknown. {@code commercial}
 * is absent for readers without price authority.
 */
public record FulfillmentLineResponse(
        UUID salesOrderLineId,
        Integer lineNo,
        UUID itemId,
        String itemSku,
        String itemName,
        String uom,
        BigDecimal orderedQuantity,
        BigDecimal qcAcceptedQuantity,
        BigDecimal deliveredQuantity,
        BigDecimal remainingToDeliverQuantity,
        BigDecimal draftDeliveryClaim,
        BigDecimal invoicedQuantity,
        @JsonInclude(JsonInclude.Include.NON_NULL) FulfillmentLineMoneyResponse commercial
) {}
