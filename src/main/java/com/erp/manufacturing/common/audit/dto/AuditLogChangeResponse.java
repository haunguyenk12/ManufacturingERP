package com.erp.manufacturing.common.audit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One field-level diff row of {@code GET /v1/audit-logs/{auditLogId}}.
 *
 * <p>{@code oldValue}/{@code newValue} are <strong>always {@code String} or {@code null}</strong> on the
 * wire, whatever the audited field actually held. The storage column is {@code jsonb}, so a snapshot can
 * be a text, a number, a boolean, an object or an array; exposing that union directly (the record carried
 * {@code JsonNode} between 2026-08-17 and 2026-08-25) makes the runtime type of a single field vary from
 * row to row, which no generated client can type and which crashed the frontend audit drawer the first
 * time a Work Order component snapshot came through as an object.
 *
 * <p>Normalisation happens in {@code AuditLogQueryService}: containers are serialised to compact JSON
 * text, scalars to their plain string form (a text value carries <em>no</em> surrounding quotes), and a
 * JSON {@code null} stays {@code null}.
 */
public record AuditLogChangeResponse(
        UUID changeId,
        String fieldName,
        String oldValue,
        String newValue,
        String changeType,
        Instant createdAt
) {
}
