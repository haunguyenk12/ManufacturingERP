package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionReceiptLineResponse(
        UUID receiptLineId,
        UUID itemId,
        String itemCode,
        UUID warehouseId,
        String warehouseCode,
        UUID lotId,
        String lotCode,
        BigDecimal quantity,
        UUID stockMovementId
) {}
