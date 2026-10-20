package com.erp.manufacturing.module.workorder.dto.execution;

import java.time.Instant;
import java.util.UUID;

/**
 * G9a (D9): one step in the history of a receipt, read from the receipt's own lifecycle columns. A receipt that
 * was withdrawn and submitted again shows its latest submission and withdrawal only — the full trail is in
 * {@code GET /v1/audit-logs?entityId=…}.
 *
 * @param action CREATED, SUBMITTED, WITHDRAWN, APPROVED, REJECTED, CANCELLED, QC_DECIDED
 */
public record ProductionReceiptEventResponse(
        String action,
        UUID actorId,
        String actorName,
        String reason,
        Instant at
) {}
