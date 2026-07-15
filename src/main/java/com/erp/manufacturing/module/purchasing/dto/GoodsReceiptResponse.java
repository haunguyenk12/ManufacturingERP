package com.erp.manufacturing.module.purchasing.dto;

import java.time.Instant;
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
        List<GoodsReceiptLineResponse> lines
) {}
