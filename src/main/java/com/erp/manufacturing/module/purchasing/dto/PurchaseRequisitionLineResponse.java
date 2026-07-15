package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseRequisitionLineResponse(
        UUID purchaseRequisitionLineId,
        UUID itemId,
        String itemCode,
        String itemName,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        BigDecimal requestedQuantity,
        BigDecimal approvedQuantity,
        LocalDate neededByDate,
        String note
) {}
