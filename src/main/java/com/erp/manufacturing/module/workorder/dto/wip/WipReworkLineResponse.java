package com.erp.manufacturing.module.workorder.dto.wip;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An execution whose rework still waits for a retry (G8d): {@code pending = rework − retryUsed}. */
public record WipReworkLineResponse(
        UUID productionExecutionId,
        String code,
        UUID workOrderOperationId,
        Integer operationSequence,
        String operationName,
        BigDecimal reworkQuantity,
        BigDecimal retryUsedQuantity,
        BigDecimal pendingReworkQuantity,
        Instant actualEndedAt
) {}
