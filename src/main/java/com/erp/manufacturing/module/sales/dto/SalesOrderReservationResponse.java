package com.erp.manufacturing.module.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One hold of stock for a sales order line (G6a). {@code remainingHeldQuantity} is what is still held. */
public record SalesOrderReservationResponse(
        UUID reservationId,
        UUID salesOrderId,
        UUID salesOrderLineId,
        UUID itemId,
        String itemSku,
        String uom,
        UUID warehouseId,
        String warehouseCode,
        UUID lotId,
        String lotCode,
        Instant lotExpiresAt,
        BigDecimal quantity,
        BigDecimal consumedQuantity,
        BigDecimal remainingHeldQuantity,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
