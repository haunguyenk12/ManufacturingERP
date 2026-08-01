package com.erp.manufacturing.common.context;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Reads the trace id of the current request so business documents can persist it (F5).
 * <p>
 * The value is the same one {@code TraceIdFilter} puts in MDC and returns as the {@code X-Trace-Id}
 * response header, but the two uses are not the same thing: the header correlates <em>logs</em>,
 * while the persisted field is <em>traceability data</em> on the document — issue, execution,
 * receipt, and stock movement can be tied back to one shop-floor action long after the logs have
 * rotated away. See {@code .claude/rules/error-handling.md} §5.1.
 * <p>
 * A bean rather than a static call so services can be unit-tested without touching MDC.
 */
@Component
public class TraceIdProvider {

    /** Null outside a request (scheduled jobs, tests) — persisting no trace is better than a fake one. */
    public String currentTraceId() {
        return MDC.get("traceId");
    }
}
