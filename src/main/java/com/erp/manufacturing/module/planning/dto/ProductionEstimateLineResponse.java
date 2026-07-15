package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionEstimateLineResponse(
        UUID componentItemId,
        String componentItemCode,
        String componentItemName,
        BigDecimal requiredQuantity,
        BigDecimal availableQuantity,
        BigDecimal shortageQuantity,
        boolean sufficient
) {}
