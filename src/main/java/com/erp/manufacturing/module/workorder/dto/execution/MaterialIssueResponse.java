package com.erp.manufacturing.module.workorder.dto.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MaterialIssueResponse(
        UUID issueId,
        UUID workOrderId,
        String status,
        String idempotencyKey,
        Instant postedAt,
        String note,
        List<MaterialIssueLineResponse> lines
) {}
