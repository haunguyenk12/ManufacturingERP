package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** G7c: a child lot a tracked purchase QC decision split off the received lot. */
public record PurchaseQcChildLotResponse(
        UUID lotId,
        String lotCode,
        UUID parentLotId,
        /** {@code AVAILABLE} for the accepted part, {@code REJECTED} for the rejected part — at the time of the decision. */
        String status,
        BigDecimal quantity,
        Instant expiresAt,
        UUID movementId
) {}
