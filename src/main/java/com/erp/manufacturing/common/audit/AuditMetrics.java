package com.erp.manufacturing.common.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audit pipeline counters (AR-9).
 *
 * <p>Deliberately free of high-cardinality labels: no user id, no entity id, no trace id. A metric
 * label is a permanent dimension in the time-series store, so tagging by identity turns a handful of
 * counters into one series per actor — the classic way an observability change becomes an outage.
 * Identity belongs in the audit row itself, which is exactly what this pipeline is for.
 *
 * <p>Gauges over the outbox backlog live on {@code AuditOutboxDispatcher}, which owns the query that
 * can answer them.
 */
@Component
public class AuditMetrics {

    public static final String PAYLOAD_TRUNCATED   = "audit_payload_truncated_total";
    public static final String SENSITIVE_DROPPED   = "audit_sensitive_field_dropped_total";
    public static final String RECORD_FAILURE      = "audit_record_failure_total";
    public static final String DISPATCH_SUCCESS    = "audit_dispatch_success_total";
    public static final String DISPATCH_FAILURE    = "audit_dispatch_failure_total";
    public static final String DISPATCH_RETRY      = "audit_dispatch_retry_total";
    public static final String DEAD_LETTER         = "audit_dead_letter_total";

    private final MeterRegistry registry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public AuditMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void payloadTruncated()          { increment(PAYLOAD_TRUNCATED); }
    public void sensitiveFieldDropped()     { increment(SENSITIVE_DROPPED); }
    public void dispatchSucceeded()         { increment(DISPATCH_SUCCESS); }
    public void dispatchFailed()            { increment(DISPATCH_FAILURE); }
    public void dispatchRetried()           { increment(DISPATCH_RETRY); }
    public void deadLettered()              { increment(DEAD_LETTER); }

    /** Source is a closed enum, so it is a safe (bounded) label; action/entity ids are not. */
    public void recordFailed(String source) {
        counters.computeIfAbsent(RECORD_FAILURE + "|" + source,
                        key -> Counter.builder(RECORD_FAILURE).tag("source", source).register(registry))
                .increment();
    }

    private void increment(String name) {
        counters.computeIfAbsent(name, key -> Counter.builder(name).register(registry)).increment();
    }

    public MeterRegistry registry() {
        return registry;
    }
}
