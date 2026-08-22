package com.erp.manufacturing.common.audit;

/**
 * Immutable field-level change carried with an {@link AuditLogEvent}.
 * Values are JSON documents because the database columns are {@code jsonb}.
 */
public record AuditFieldChange(
        String fieldName,
        String oldValue,
        String newValue,
        AuditLogChangeType changeType
) {
}
