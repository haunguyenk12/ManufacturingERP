package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaterialReservationResponse(
        UUID reservationId,
        UUID workOrderId,
        UUID componentLineId,
        UUID itemId,
        String itemSku,
        UUID warehouseId,
        String warehouseCode,
        UUID lotId,
        String lotNumber,
        BigDecimal quantity,
        BigDecimal consumedQuantity,
        BigDecimal remainingQuantity,
        String status,
        Instant createdAt,
        Instant updatedAt,
        /** G8 (D5): the work order's version after this response — send it with the next command. */
        Long workOrderVersion
) {

    /** Shape before G8 — callers that have no work order version to report. */
    public MaterialReservationResponse(UUID reservationId, UUID workOrderId, UUID componentLineId, UUID itemId,
                                       String itemSku, UUID warehouseId, String warehouseCode, UUID lotId,
                                       String lotNumber, BigDecimal quantity, BigDecimal consumedQuantity,
                                       BigDecimal remainingQuantity, String status, Instant createdAt,
                                       Instant updatedAt) {
        this(reservationId, workOrderId, componentLineId, itemId, itemSku, warehouseId, warehouseCode, lotId,
                lotNumber, quantity, consumedQuantity, remainingQuantity, status, createdAt, updatedAt, null);
    }
}
