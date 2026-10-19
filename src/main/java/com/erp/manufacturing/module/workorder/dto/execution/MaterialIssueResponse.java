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
        String rejectionReason,
        /** G8b: the issue's own version — send it as {@code expectedIssueVersion} with a decision. */
        Long version,
        /** G8b: the work order's version as of this response — the {@code expectedWorkOrderVersion} to send next. */
        Long workOrderVersion,
        /** G8b: requested as a supplemental issue. */
        boolean supplemental,
        /** G8b: who made the request (the document's creator); only they may withdraw it. */
        UUID requestedByUserId,
        /** G8b: why the requester withdrew it; {@code null} unless {@code CANCELLED} by them. */
        String cancellationReason
) {

    /** Shape before G8b. */
    public MaterialIssueResponse(UUID issueId, String code, UUID workOrderId, String workOrderCode, String status,
                                 String idempotencyKey, String traceId, Instant postedAt, String createdByUsername,
                                 String note, List<MaterialIssueLineResponse> lines, Instant requestedAt,
                                 Instant decidedAt, UUID decidedBy, String rejectionReason) {
        this(issueId, code, workOrderId, workOrderCode, status, idempotencyKey, traceId, postedAt, createdByUsername,
                note, lines, requestedAt, decidedAt, decidedBy, rejectionReason, null, null, false, null, null);
    }
}
