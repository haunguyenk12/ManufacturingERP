package com.erp.manufacturing.common.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Detail shape for {@code GET /audit-logs/{id}} with persisted field-level changes. */
public record AuditLogDetailResponse(
        UUID auditId,
        UUID userId,
        String username,
        String action,
        String entityType,
        String entityName,
        String description,
        String status,
        String clientIp,
        String userAgent,
        String traceId,
        UUID plantId,
        Instant createdAt,
        List<AuditLogChangeResponse> changes
) {
}
