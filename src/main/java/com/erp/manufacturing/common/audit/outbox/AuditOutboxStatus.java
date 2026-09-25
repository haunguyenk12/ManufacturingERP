package com.erp.manufacturing.common.audit.outbox;

/**
 * Lifecycle of one {@code audit_outbox} row.
 *
 * <p>{@link #FAILED} rows are never deleted by the application. A dead-lettered audit event is
 * evidence that something was not recorded; deleting it would destroy the only signal that the trail
 * has a hole. They are replayed by operators after the cause is fixed.
 */
public enum AuditOutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
