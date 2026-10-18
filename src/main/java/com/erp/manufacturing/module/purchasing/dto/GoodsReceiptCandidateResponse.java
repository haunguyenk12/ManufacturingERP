package com.erp.manufacturing.module.purchasing.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** G7b: a purchase order open for receiving, with only its still-open lines. */
public record GoodsReceiptCandidateResponse(
        UUID purchaseOrderId,
        String purchaseOrderCode,
        Long version,
        String status,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        LocalDate orderDate,
        String reference,
        List<GoodsReceiptCandidateLineResponse> lines
) {}
