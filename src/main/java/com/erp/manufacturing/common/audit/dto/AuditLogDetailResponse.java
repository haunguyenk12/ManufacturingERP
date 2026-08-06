package com.erp.manufacturing.common.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detail shape for {@code GET /audit-logs/{id}} (C2-1) — same fields as {@link AuditLogResponse} plus
 * {@code changes[]}, populated by a real query against {@code audit_log_changes}. Genuinely empty
 * today (nothing writes to that table yet), not a hardcoded {@code []}.
 */
public record AuditLogDetailResponse(
        UUID auditId,
        UUID userId,
        String username,
        String action,
        String entityType,
        String entityId,
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
