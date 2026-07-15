package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItemSupplierResponse(
        UUID itemSupplierId,
        UUID itemId,
        String itemCode,
        String itemName,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        String supplierItemCode,
        Integer leadTimeDays,
        BigDecimal minimumOrderQuantity,
        BigDecimal unitPrice,
        String currencyCode,
        boolean preferred,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
