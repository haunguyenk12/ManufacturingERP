package com.erp.manufacturing.module.purchasing.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID purchaseOrderId,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        UUID warehouseId,
        String warehouseCode,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        String purchaseOrderNo,
        String status,
        LocalDate orderDate,
        LocalDate expectedDate,
        UUID sourceRequisitionId,
        String note,
        Instant createdAt,
        Instant updatedAt,
        List<PurchaseOrderLineResponse> lines
) {}
