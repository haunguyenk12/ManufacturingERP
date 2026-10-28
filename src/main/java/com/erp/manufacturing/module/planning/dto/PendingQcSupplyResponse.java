package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CF-4: one goods receipt line holding pending purchase-QC quantity when the run ran, as the run captured it. Minimal on
 * purpose — no price, no supplier contact — so {@code PERM_MRP_READ} is enough to read it (FE Q2).
 */
public record PendingQcSupplyResponse(
        UUID sourceId,
        UUID requirementLineId,
        UUID goodsReceiptId,
        UUID goodsReceiptLineId,
        /** Business code of the receipt; show it instead of the id. */
        String receiptCode,
        /** Receipt {@code version} at the moment the run captured it. */
        Long receiptVersion,
        UUID itemId,
        UUID warehouseId,
        String uomCode,
        BigDecimal pendingQuantity
) {}
