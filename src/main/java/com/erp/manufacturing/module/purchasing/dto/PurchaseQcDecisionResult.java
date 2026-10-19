package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;

/** G7c: response of a QC decision — the decision plus the line's new totals and the receipt's new version. */
public record PurchaseQcDecisionResult(
        PurchaseQcDecisionResponse decision,
        Long receiptVersion,
        BigDecimal receivedQuantity,
        BigDecimal acceptedQuantity,
        BigDecimal rejectedQuantity,
        BigDecimal pendingQuantity
) {}
