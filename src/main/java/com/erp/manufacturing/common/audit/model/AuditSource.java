package com.erp.manufacturing.common.audit.model;

/**
 * Where the audited event originated (AR-3).
 *
 * <p>Before this existed the pipeline assumed every event came from an HTTP request: the aspect
 * called {@code RequestContextHolder.currentRequestAttributes()}, which throws outside a request
 * thread. That assumption is what makes a scheduled job or a message consumer unauditable today, so
 * the origin has to be part of the event rather than something inferred from thread state.
 */
public enum AuditSource {
    /** Servlet request handled by a controller. */
    HTTP,
    /** Authentication flow — may run before any principal exists (failed login). */
    AUTH,
    /** {@code @Scheduled} / maintenance task. */
    SCHEDULED_JOB,
    /** Message / event consumer. */
    MESSAGE,
    /** Bulk or import processing acting on behalf of a user. */
    BATCH,
    /** The application acting on its own behalf (bootstrap, migration, dispatcher). */
    SYSTEM
}
