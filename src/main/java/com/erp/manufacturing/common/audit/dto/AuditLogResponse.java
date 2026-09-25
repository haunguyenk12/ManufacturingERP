package com.erp.manufacturing.common.audit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary shape for {@code GET /v1/audit-logs}. Changes and entity targets remain exclusive to
 * {@link AuditLogDetailResponse}, so a page of 20 rows does not fan out into 40 child queries.
 *
 * <p><strong>All AR-6 additions are additive</strong> — nothing was removed or renamed, so a client
 * written against the previous shape keeps working. {@code status} and {@code createdAt} are retained
 * for that compatibility window even though {@code outcome} and {@code occurredAt} are now the
 * canonical spellings; a deprecation date has to be agreed with the frontend before either goes.
 *
 * @param entityId    restored: it was dropped from the response when {@code entityName} was added, so
 *                    a client could see <em>which kind</em> of object an event was about but had no
 *                    stable key to link to it or to filter by
 * @param outcome     canonical form of {@code status} ({@code SUCCESS} / {@code FAILURE})
 * @param reasonCode  stable machine-readable reason, in place of parsing a human message
 * @param source      HTTP / AUTH / SCHEDULED_JOB / MESSAGE / BATCH / SYSTEM; {@code null} pre-refactor
 * @param occurredAt  when the action happened, as opposed to {@code createdAt}, when the row landed
 * @param status      legacy alias of {@code outcome}, kept for compatibility
 * @param createdAt   legacy field; equals the materialisation time, not the action time
 */
public record AuditLogResponse(
        UUID auditId,
        UUID userId,
        String username,
        String action,
        String entityType,
        String entityId,
        String entityName,
        String description,
        String outcome,
        String reasonCode,
        String source,
        String clientIp,
        String userAgent,
        String traceId,
        String httpMethod,
        String requestPath,
        UUID companyId,
        UUID plantId,
        UUID warehouseId,
        Instant occurredAt,
        String status,
        Instant createdAt
) {
}
