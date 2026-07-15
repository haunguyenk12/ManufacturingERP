package com.erp.manufacturing.module.purchasing.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseRequisitionResponse(
        UUID purchaseRequisitionId,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        UUID warehouseId,
        String warehouseCode,
        String requisitionNo,
        String status,
        LocalDate neededByDate,
        String sourceType,
        UUID sourceId,
        String decisionNote,
        Instant createdAt,
        Instant updatedAt,
        List<PurchaseRequisitionLineResponse> lines
) {}
