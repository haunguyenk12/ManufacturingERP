package com.erp.manufacturing.module.workorder.dto.execution;

import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record WipTransactionRequest(
        @NotNull WipTransactionType transactionType,
        @Size(max = 80) String stageCode,
        @NotNull @Positive BigDecimal quantity,
        Instant occurredAt,
        @Size(max = 1000) String note
) {}
