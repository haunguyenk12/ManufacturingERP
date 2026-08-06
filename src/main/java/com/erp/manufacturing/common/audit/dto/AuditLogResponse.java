package com.erp.manufacturing.common.audit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary shape for {@code GET /audit-logs} (C2-1). No {@code changes[]} here — it is always empty
 * today (đợt 2, field-level diff capture, is not in scope), so fetching it per row on every page would
 * be a batch query for a guaranteed-empty result. See {@link AuditLogDetailResponse} for the shape
 * that does include it.
 */
public record AuditLogResponse(
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
        /** Always {@code null} today — see {@code AuditLog.plantId} javadoc. */
        UUID plantId,
        Instant createdAt
) {
}
