package com.erp.manufacturing.common.audit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary shape for {@code GET /audit-logs}. Changes remain exclusive to
 * {@link AuditLogDetailResponse} to avoid a child query for every row in a page.
 */
public record AuditLogResponse(
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
        /** Always {@code null} today — see {@code AuditLog.plantId} javadoc. */
        UUID plantId,
        Instant createdAt
) {
}
