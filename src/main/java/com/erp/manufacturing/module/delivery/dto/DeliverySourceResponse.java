package com.erp.manufacturing.module.delivery.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One stock position a line can be delivered from (FE R04/R08). Only {@code AVAILABLE}, unexpired lots appear.
 * {@code freeQuantity} is on hand minus every hold and quality hold; {@code ownReservedQuantity} is this line's own
 * holds there; {@code availableForLine} = min(free + own, what the line can still claim). Holds of other orders are
 * never part of either figure (FE A09). {@code sourceKey} is opaque: send it back unchanged.
 */
public record DeliverySourceResponse(
        String sourceKey,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        UUID itemId,
        String uom,
        UUID lotId,
        String lotCode,
        Instant expiresAt,
        BigDecimal freeQuantity,
        BigDecimal ownReservedQuantity,
        BigDecimal availableForLine
) {}
