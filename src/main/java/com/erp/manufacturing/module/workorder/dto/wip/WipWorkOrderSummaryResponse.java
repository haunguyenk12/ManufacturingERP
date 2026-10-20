package com.erp.manufacturing.module.workorder.dto.wip;

import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the plant WIP list (G8d). Output quantities only — material lines differ in unit and are read per work
 * order. {@code finalGoodQuantity} and {@code availableToReceipt} are null for a work order reported before G8c.
 */
public record WipWorkOrderSummaryResponse(
        UUID workOrderId,
        String workOrderNo,
        WorkOrderStatus status,
        Long version,
        UUID productItemId,
        String productItemSku,
        String productItemName,
        String uom,
        BigDecimal plannedQuantity,
        boolean inputKnown,
        BigDecimal finalGoodQuantity,
        BigDecimal scrapQuantity,
        BigDecimal historicalReworkQuantity,
        BigDecimal pendingReworkQuantity,
        BigDecimal approvedReceiptQuantity,
        BigDecimal availableToReceipt,
        BigDecimal terminalShortfallQuantity,
        Instant createdAt
) {}
