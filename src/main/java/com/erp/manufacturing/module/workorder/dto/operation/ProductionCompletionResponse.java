package com.erp.manufacturing.module.workorder.dto.operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** G8c: the work order after complete-production. Nothing was received, closed or posted to stock. */
public record ProductionCompletionResponse(
        UUID workOrderId,
        String workOrderNo,
        String status,
        Long version,
        BigDecimal plannedQuantity,
        BigDecimal finalGoodQuantity,
        /** {@code max(0, planned − final good)}, captured once at completion. */
        BigDecimal terminalShortfallQuantity,
        String shortfallReason,
        Instant completedAt
) {}
