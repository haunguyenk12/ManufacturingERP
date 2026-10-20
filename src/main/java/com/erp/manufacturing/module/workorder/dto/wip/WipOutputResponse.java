package com.erp.manufacturing.module.workorder.dto.wip;

import java.math.BigDecimal;

/**
 * Output of a work order and what still waits for a production receipt (G8d). {@code approvedReceiptQuantity} is what
 * approved receipts put in stock, {@code openReceiptQuantity} what draft and pending receipts already claim, and
 * {@code availableToReceipt = finalGood − approved} (open receipts are not deducted, as everywhere else — B16). Final
 * good and the receipt allowance are null for a work order reported before G8c.
 */
public record WipOutputResponse(
        String uom,
        BigDecimal plannedQuantity,
        BigDecimal finalGoodQuantity,
        BigDecimal scrapQuantity,
        BigDecimal historicalReworkQuantity,
        BigDecimal approvedReceiptQuantity,
        BigDecimal openReceiptQuantity,
        BigDecimal availableToReceipt,
        BigDecimal terminalShortfallQuantity
) {}
