package com.erp.manufacturing.common.audit.model;

import com.erp.manufacturing.common.audit.AuditLogChangeType;

/**
 * The create/update/delete semantics of an audited command, declared rather than guessed (AR-4).
 *
 * <p>The previous implementation derived this from the <em>spelling of the enum constant</em>: a name
 * ending in {@code _CREATED} meant CREATE, {@code _DELETED} meant DELETE, everything else meant
 * UPDATE. That silently mislabels every action that does not follow the convention —
 * {@code BOM_ACTIVATED}, {@code WORK_ORDER_RELEASED}, {@code PERMISSION_GRANTED},
 * {@code ROLE_REVOKED} — and a revoke recorded as an UPDATE diff is not a naming nit: it is the
 * difference between "this grant was removed" and "something about this role changed".
 *
 * <p>{@link #INFERRED} keeps the old heuristic for the call sites that have not been migrated yet, so
 * the switch to explicit semantics can happen per module instead of in one large untested change.
 */
public enum AuditOperation {

    /** Fall back to the legacy action-suffix heuristic. Migrating away from this is the point. */
    INFERRED,
    CREATE,
    UPDATE,
    DELETE,
    /** The command changed state without a field-level diff worth recording. */
    NONE;

    /** {@code null} for {@link #INFERRED} and {@link #NONE}: the caller decides what to do. */
    public AuditLogChangeType toChangeType() {
        return switch (this) {
            case CREATE -> AuditLogChangeType.CREATE;
            case UPDATE -> AuditLogChangeType.UPDATE;
            case DELETE -> AuditLogChangeType.DELETE;
            case INFERRED, NONE -> null;
        };
    }
}
