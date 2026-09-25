package com.erp.manufacturing.common.audit.dto;

/**
 * One object an audit event touched (AR-6).
 *
 * <p>Exactly one row has {@code relation = "PRIMARY"} — the object the action is named after, the
 * same one the legacy {@code entityType}/{@code entityId}/{@code entityName} fields carry. Any
 * {@code "RELATED"} rows are the other participants, which the flat fields have no room for: the
 * permission in a grant, the user and scope in an assignment, the line in a BOM edit.
 *
 * <p>Empty for every event recorded before the audit refactor. That is an absence of detail, not a
 * statement that the event touched nothing.
 */
public record AuditLogEntityResponse(
        String relation,
        String entityType,
        String entityId,
        String entityName
) {
}
