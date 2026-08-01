package com.erp.manufacturing.module.workorder.dto.execution;

import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WipTransactionRequest(
        @NotNull WipTransactionType transactionType,
        /**
         * Operation of this work order's routing snapshot (F5). When given it wins over
         * {@code stageCode}, which stays available for work orders created without a routing.
         */
        UUID workOrderOperationId,
        @Size(max = 80) String stageCode,
        @NotNull @Positive BigDecimal quantity,
        Instant occurredAt,
        @Size(max = 1000) String note
) {}
