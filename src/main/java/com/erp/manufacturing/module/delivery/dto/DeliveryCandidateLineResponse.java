package com.erp.manufacturing.module.delivery.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One sales order line as a delivery candidate (FE R04). The delivered and claimed figures are {@code null} when the
 * order's delivery baseline is unknown (D3), never 0. {@code availableToClaimQuantity} = ordered - delivered - the
 * other drafts' claims (the draft being edited excluded).
 */
public record DeliveryCandidateLineResponse(
        UUID salesOrderLineId,
        Integer lineNo,
        UUID itemId,
        String itemCode,
        String itemName,
        String uom,
        LocalDate dueDate,
        BigDecimal orderedQuantity,
        BigDecimal deliveredQuantity,
        BigDecimal ownDraftClaimQuantity,
        BigDecimal otherDraftClaimQuantity,
        BigDecimal availableToClaimQuantity,
        BigDecimal heldStockQuantity
) {}
