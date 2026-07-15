package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkOrderResponse(
        UUID workOrderId,
        UUID companyId,
        UUID plantId,
        String plantCode,
        String workOrderNo,
        UUID productItemId,
        String productItemCode,
        String productItemName,
        UUID bomId,
        String bomRevision,
        UUID outputWarehouseId,
        String outputWarehouseCode,
        BigDecimal plannedQuantity,
        BigDecimal completedQuantity,
        BigDecimal remainingQuantity,
        String status,
        Instant plannedStartAt,
        Instant plannedEndAt,
        Instant releasedAt,
        Instant completedAt,
        Instant cancelledAt,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        List<WorkOrderComponentLineResponse> componentLines
) {}
