package com.erp.manufacturing.common.audit.model;

/**
 * One object touched by an audited command.
 *
 * <p>A single {@code entity_type}/{@code entity_id} pair on the audit row cannot describe a command
 * that connects two objects. "Permission granted" is the clearest case: the row named the role and
 * left the permission unrecorded, so the audit trail could say a role changed but not what it gained
 * — the one fact an investigator needs. Events therefore carry one {@code PRIMARY} ref plus any
 * number of {@code RELATED} refs, persisted to {@code audit_log_entities}.
 *
 * @param relation   whether this is the object the action is named after, or a participant
 * @param entityType JPA entity simple name, e.g. {@code "Role"}
 * @param entityId   primary key rendered as text; {@code null} when the command did not resolve one
 * @param entityName human-readable label snapshotted at the time of the action
 */
public record AuditEntityRef(
        AuditEntityRelation relation,
        String entityType,
        String entityId,
        String entityName
) {

    public static AuditEntityRef primary(String entityType, Object entityId, String entityName) {
        return new AuditEntityRef(AuditEntityRelation.PRIMARY, entityType, asText(entityId), entityName);
    }

    public static AuditEntityRef related(String entityType, Object entityId, String entityName) {
        return new AuditEntityRef(AuditEntityRelation.RELATED, entityType, asText(entityId), entityName);
    }

    public boolean isPrimary() {
        return relation == AuditEntityRelation.PRIMARY;
    }

    /** {@code true} when the ref carries nothing worth persisting. */
    public boolean isEmpty() {
        return isBlank(entityType) && isBlank(entityId) && isBlank(entityName);
    }

    private static String asText(Object entityId) {
        return entityId == null ? null : entityId.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum AuditEntityRelation {
        PRIMARY,
        RELATED
    }
}
