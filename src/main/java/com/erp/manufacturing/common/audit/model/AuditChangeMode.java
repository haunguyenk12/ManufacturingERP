package com.erp.manufacturing.common.audit.model;

/**
 * How field-level changes are produced for an audited method (AR-4).
 *
 * <p>The previous implementation had no such switch: every audited method got the same automatic
 * diff, produced by intersecting a JPA entity snapshot taken before the call with the response DTO
 * returned after it. That works for a flat update and is misleading everywhere else — the two shapes
 * only overlap on scalar fields, so a command that replaces a collection reports "no changes", and a
 * command whose response is a different aggregate reports changes that never happened.
 */
public enum AuditChangeMode {

    /** Record the event, capture no field diff. Correct for state transitions and reads. */
    NONE,

    /**
     * Diff scalar and singular-association fields of the target entity. Explicitly does <em>not</em>
     * claim to handle collections or nested aggregates; those need {@link #CUSTOM}.
     */
    AUTO,

    /**
     * Delegate to an {@code AuditChangeProvider} that understands this aggregate. Required for
     * anything whose meaningful change lives in a child collection (BOM lines, role permissions,
     * scope resources).
     */
    CUSTOM
}
