package com.erp.manufacturing.module.delivery.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One delivery line. {@code reservedQuantity}/{@code freeQuantity} and the two movement ids are {@code null} until
 * POST: then the part taken from the order's own holds and the part taken from free stock, one {@code ISSUE} each.
 */
public record DeliveryLineResponse(
        UUID deliveryLineId,
        Integer lineNo,
        UUID salesOrderLineId,
        Integer salesOrderLineNo,
        UUID itemId,
        String itemCode,
        String itemName,
        String uom,
        UUID warehouseId,
        String warehouseCode,
        UUID lotId,
        String lotCode,
        String sourceKey,
        BigDecimal quantity,
        BigDecimal reservedQuantity,
        BigDecimal freeQuantity,
        UUID reservedMovementId,
        UUID freeMovementId
) {}
