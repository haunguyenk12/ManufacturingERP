package com.erp.manufacturing.common.audit.context;

import java.util.Optional;

/**
 * Supplies the ambient {@link AuditContext} for whatever is currently executing (AR-3).
 *
 * <p>Providers are consulted in {@link #order()} sequence and the first one that can answer wins, so
 * an explicitly pushed context (a scheduled job, a message consumer) always beats the HTTP provider,
 * and the system provider is the last-resort answer that can never fail. The point of the interface
 * is that the audit aspect stops asking "is there a servlet request?" and starts asking "who is
 * acting?" — a question every execution model can answer.
 */
public interface AuditContextProvider {

    /** Lower runs first. */
    int order();

    /** {@code Optional.empty()} when this provider has nothing to say about the current thread. */
    Optional<AuditContext> current();
}
