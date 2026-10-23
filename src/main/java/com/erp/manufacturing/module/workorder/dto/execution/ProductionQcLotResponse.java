package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** G9c: a lot a QC decision put a quantity in. {@code quantity} is what the decision moved, not current stock. */
public record ProductionQcLotResponse(
        UUID lotId,
        String lotNumber,
        UUID parentLotId,
        String status,
        BigDecimal quantity,
        Instant expiresAt,
        UUID stockMovementId
) {}
