package com.erp.manufacturing.common.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogChangeResponse(
        UUID changeId,
        String fieldName,
        String oldValue,
        String newValue,
        String changeType,
        Instant createdAt
) {
}
