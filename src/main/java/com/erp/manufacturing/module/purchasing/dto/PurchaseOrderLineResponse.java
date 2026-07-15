package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseOrderLineResponse(
        UUID purchaseOrderLineId,
        UUID purchaseRequisitionLineId,
        UUID itemId,
        String itemCode,
        String itemName,
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        BigDecimal remainingQuantity,
        BigDecimal unitPrice,
        String currencyCode,
        LocalDate expectedDate
) {}
