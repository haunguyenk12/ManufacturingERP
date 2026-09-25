package com.erp.manufacturing.common.audit.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Schedules the outbox drain (AR-2). Holds no transaction of its own and does no work directly —
 * every unit of work goes through {@link AuditOutboxProcessor}, so the {@code REQUIRES_NEW} boundary
 * is a real proxy call rather than a self-invocation that Spring would silently ignore.
 *
 * <p>Each pass reclaims expired leases first, then claims and delivers batches until a short batch
 * shows the queue is drained. A pass that throws is logged and the next tick simply tries again: a
 * dispatcher outage delays audit rows, it never loses them, because the events are already durable in
 * the outbox.
 *
 * <p>There is deliberately no {@code CallerRunsPolicy} anywhere on this path. Under load the backlog
 * stays in the database; the pre-refactor executor did the opposite and turned audit-write pressure
 * into request latency on the caller's own thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditOutboxDispatcher {

    /** Distinguishes lease holders in logs when several instances drain the same queue. */
    private final String workerId = "audit-" + UUID.randomUUID().toString().substring(0, 8);

    private final AuditOutboxProcessor processor;
    private final AuditOutboxProperties properties;

    @Scheduled(fixedDelayString = "${app.audit.outbox.poll-interval-ms:1000}")
    public void drain() {
        if (!properties.dispatcherEnabled()) {
            return;
        }
        try {
            processor.reclaimExpiredLeases();
            int claimed;
            do {
                claimed = drainOnce();
            } while (claimed == properties.batchSize());
        } catch (Exception e) {
            log.error("[Audit] Outbox dispatcher pass failed error={}", e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Claims and delivers one batch.
     *
     * @return how many rows were claimed; a full batch means more are probably waiting
     */
    public int drainOnce() {
        List<UUID> claimed = processor.claimBatch(workerId);
        claimed.forEach(processor::deliver);
        return claimed.size();
    }

    /** Housekeeping for drained rows; hourly is ample for a table that only grows between passes. */
    @Scheduled(fixedDelayString = "${app.audit.outbox.prune-interval-ms:3600000}")
    public void pruneProcessed() {
        if (!properties.dispatcherEnabled()) {
            return;
        }
        try {
            int pruned = processor.pruneProcessed();
            if (pruned > 0) {
                log.info("[Audit] Pruned {} drained outbox rows", pruned);
            }
        } catch (Exception e) {
            log.error("[Audit] Outbox prune failed error={}", e.getClass().getSimpleName(), e);
        }
    }

    public String workerId() {
        return workerId;
    }
}
