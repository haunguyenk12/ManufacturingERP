package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkOrderComponentLineResponse(
        UUID componentLineId,
        UUID bomLineId,
        Integer lineNo,
        UUID componentItemId,
        String componentItemCode,
        String componentItemName,
        BigDecimal quantityPer,
        BigDecimal scrapRate,
        BigDecimal requiredQuantity,
        BigDecimal issuedQuantity,
        BigDecimal remainingQuantity
) {}
