package com.erp.manufacturing.common.audit.model;

/**
 * Whether the audited command completed or was rejected.
 *
 * <p>This is the canonical form of what the {@code audit_logs.status} column has always held as free
 * text ({@code "SUCCESS"} / {@code "FAILURE"}). The column keeps its name and its wire contract; the
 * enum exists so nothing in the pipeline can invent a third spelling.
 */
public enum AuditOutcome {
    SUCCESS,
    FAILURE
}
