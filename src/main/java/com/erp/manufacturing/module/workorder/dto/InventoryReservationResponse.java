package com.erp.manufacturing.module.workorder.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One work-order reservation as seen from the warehouse (G4-4, "Giữ cho sản xuất").
 * {@code remainingQuantity = quantity − consumedQuantity} — the part still held back from free stock
 * while the reservation is {@code ACTIVE}.
 */
public record InventoryReservationResponse(
        UUID reservationId,
        UUID workOrderId,
        String workOrderNo,
        String workOrderStatus,
        UUID componentLineId,
        Integer componentLineNo,
        UUID itemId,
        String itemCode,
        String itemName,
        String uom,
        UUID warehouseId,
        UUID lotId,
        String lotCode,
        BigDecimal quantity,
        BigDecimal consumedQuantity,
        BigDecimal remainingQuantity,
        String status,
        Instant createdAt
) {}
