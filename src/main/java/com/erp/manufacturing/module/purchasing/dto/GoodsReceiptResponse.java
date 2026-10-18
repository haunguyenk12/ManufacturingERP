package com.erp.manufacturing.module.purchasing.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptResponse(
        UUID goodsReceiptId,
        UUID purchaseOrderId,
        UUID warehouseId,
        String warehouseCode,
        String receiptNo,
        String status,
        Instant postedAt,
        String idempotencyKey,
        String note,
        Instant cancelledAt,
        String cancelNote,
        Instant createdAt,
        Instant updatedAt,
        List<GoodsReceiptLineResponse> lines,
        // ── G7b, additive ──
        /** Same value as {@code receiptNo} (FE name). */
        String code,
        Long version,
        LocalDate receivedDate,
        String reference,
        UUID companyId,
        UUID plantId,
        String purchaseOrderCode,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        String warehouseName,
        UUID postedBy,
        UUID cancelledBy,
        /** The purchase order's status and version after this receipt — refresh it without another call. */
        String purchaseOrderStatus,
        Long purchaseOrderVersion
) {

    /** Pre-G7b arity, for callers and tests that build the legacy shape. */
    public GoodsReceiptResponse(UUID goodsReceiptId, UUID purchaseOrderId, UUID warehouseId, String warehouseCode,
                                String receiptNo, String status, Instant postedAt, String idempotencyKey, String note,
                                Instant cancelledAt, String cancelNote, Instant createdAt, Instant updatedAt,
                                List<GoodsReceiptLineResponse> lines) {
        this(goodsReceiptId, purchaseOrderId, warehouseId, warehouseCode, receiptNo, status, postedAt, idempotencyKey,
                note, cancelledAt, cancelNote, createdAt, updatedAt, lines, receiptNo, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }
}
