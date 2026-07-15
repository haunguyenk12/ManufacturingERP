package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaterialReservationResponse(
        UUID reservationId,
        UUID workOrderId,
        UUID componentLineId,
        UUID itemId,
        String itemCode,
        UUID warehouseId,
        String warehouseCode,
        UUID lotId,
        String lotCode,
        BigDecimal quantity,
        BigDecimal consumedQuantity,
        BigDecimal remainingQuantity,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
