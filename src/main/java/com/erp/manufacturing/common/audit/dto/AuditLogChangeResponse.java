package com.erp.manufacturing.common.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditLogChangeResponse(
        UUID changeId,
        String fieldName,
        JsonNode oldValue,
        JsonNode newValue,
        String changeType,
        Instant createdAt
) {
}
