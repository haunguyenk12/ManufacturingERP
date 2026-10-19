package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * G7c: one goods receipt line waiting for purchase QC. No price, supplier contact or notes — the queue
 * is the QC-only workspace (FE §3.2). {@code goodsReceiptVersion} is what a decision sends as
 * {@code expectedVersion}.
 */
public record PurchaseQcQueueLineResponse(
        UUID goodsReceiptId,
        String goodsReceiptCode,
        Long goodsReceiptVersion,
        LocalDate receivedDate,
        Instant postedAt,
        UUID purchaseOrderId,
        String purchaseOrderCode,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        UUID goodsReceiptLineId,
        UUID itemId,
        String itemCode,
        String itemName,
        String uomCode,
        String trackingPolicy,
        UUID lotId,
        String lotCode,
        LocalDate expiresOn,
        Instant lotExpiresAt,
        BigDecimal receivedQuantity,
        BigDecimal acceptedQuantity,
        BigDecimal rejectedQuantity,
        BigDecimal pendingQuantity,
        UUID qualityBucketId
) {}
