package com.erp.manufacturing.module.workorder.dto.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductionReceiptResponse(
        UUID receiptId,
        UUID workOrderId,
        String status,
        String idempotencyKey,
        Instant postedAt,
        String note,
        List<ProductionReceiptLineResponse> lines
) {}
