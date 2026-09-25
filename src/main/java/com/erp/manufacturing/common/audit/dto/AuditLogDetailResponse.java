package com.erp.manufacturing.common.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detail shape for {@code GET /v1/audit-logs/{auditLogId}} (AR-6).
 *
 * <p>Carries everything the summary carries plus the two child collections: {@code entities} (every
 * object the event touched, not just the one it is named after) and {@code changes} (the field-level
 * diff). {@code metadata} is an allow-listed JSON document supplied by the command, capped and
 * validated before storage — for example {@code {"noOp":true}} on a privilege grant that changed
 * nothing.
 *
 * @param status    legacy alias of {@code outcome}, kept for the compatibility window
 * @param createdAt legacy field; the row's materialisation time, not the action time
 */
public record AuditLogDetailResponse(
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
        String metadata,
        Instant occurredAt,
        String status,
        Instant createdAt,
        List<AuditLogEntityResponse> entities,
        List<AuditLogChangeResponse> changes
) {
}
