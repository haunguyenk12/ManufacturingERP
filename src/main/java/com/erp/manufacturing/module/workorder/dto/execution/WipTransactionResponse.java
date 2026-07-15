package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WipTransactionResponse(
        UUID wipTransactionId,
        UUID workOrderId,
        String transactionType,
        String stageCode,
        BigDecimal quantity,
        String referenceType,
        String referenceId,
        Instant occurredAt,
        String note
) {}
