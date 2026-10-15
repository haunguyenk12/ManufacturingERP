package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockTransferLineResponse(
        UUID transferLineId,
        int lineNo,
        UUID itemId,
        String itemCode,
        String itemName,
        String uom,
        UUID lotId,
        String lotCode,
        Instant lotExpiresAt,
        BigDecimal quantity,
        UUID outMovementId,
        UUID inMovementId
) {}
