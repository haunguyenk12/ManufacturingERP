package com.erp.manufacturing.module.workorder.dto.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MaterialIssueResponse(
        UUID issueId,
        /** Document number (spec §4.2 "History"), e.g. {@code MI-3F2A9C01}. */
        String code,
        UUID workOrderId,
        /** Work order document number (spec §4.2 "History"). */
        String workOrderCode,
        String status,
        String idempotencyKey,
        /** Business trace of this document. Not the {@code X-Trace-Id} debug header. */
        String traceId,
        Instant postedAt,
        /**
         * Who posted the issue (spec §4.2 "History: createdBy"). Resolved in one batch query per
         * page (rule C14); null when the user no longer exists.
         */
        String createdByUsername,
        String note,
        List<MaterialIssueLineResponse> lines,
        Instant requestedAt,
        Instant decidedAt,
        UUID decidedBy,
        String rejectionReason
) {}
