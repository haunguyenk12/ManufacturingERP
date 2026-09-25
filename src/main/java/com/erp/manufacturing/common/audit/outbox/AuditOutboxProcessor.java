package com.erp.manufacturing.common.audit.outbox;

import com.erp.manufacturing.common.audit.AuditLogMaterializer;
import com.erp.manufacturing.common.audit.AuditMetrics;
import com.erp.manufacturing.common.audit.AuditPayloadCodec;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The transactional half of the outbox drain (AR-2).
 *
 * <p><strong>Why this is a separate bean from {@code AuditOutboxDispatcher}.</strong> Spring applies
 * {@code @Transactional} through a proxy, so a scheduled method calling {@code claimBatch()} on
 * {@code this} would run it with no transaction at all — the annotation silently ignored, the lease
 * never committed, and two workers free to process the same row. The same trap has already cost this
 * codebase a real security hole once ({@code CLAUDE.md §0.19} consequence #3, where a controller alias
 * delegating to its own service bypassed {@code @PreAuthorize}). Splitting the beans is what makes the
 * proxy boundary real instead of assumed.
 *
 * <p>Every method here opens {@code REQUIRES_NEW} rather than {@code REQUIRED}: the scheduler has no
 * transaction to join, and one row's failure must never roll back another's success.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditOutboxProcessor {

    private final AuditOutboxRepository outboxRepository;
    private final AuditLogMaterializer materializer;
    private final AuditPayloadCodec codec;
    private final AuditOutboxProperties properties;
    private final AuditMetrics metrics;
    private final Clock clock;

    /**
     * Marks a batch of due rows {@code PROCESSING} under a lease, returning their ids.
     *
     * <p>Only ids cross the transaction boundary. Handing back managed entities would let the caller
     * touch them after the transaction that loaded them has closed, which is how a lazy-loading
     * exception appears in a place that looks unrelated.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> claimBatch(String workerId) {
        Instant now = clock.instant();
        List<AuditOutboxEntry> due = outboxRepository.claimDueBatch(
                now, PageRequest.ofSize(properties.batchSize()));
        due.forEach(entry -> entry.claim(workerId, now));
        return due.stream().map(AuditOutboxEntry::getOutboxId).toList();
    }

    /** One row, one transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UUID outboxId) {
        AuditOutboxEntry entry = outboxRepository.findById(outboxId).orElse(null);
        if (entry == null || entry.getStatus() != AuditOutboxStatus.PROCESSING) {
            return;
        }
        try {
            AuditRecordDraft draft = codec.decode(entry.getPayload());
            materializer.materialize(draft, codec.hash(codec.toNode(draft)));
            entry.markProcessed(clock.instant());
            metrics.dispatchSucceeded();
        } catch (Exception e) {
            String errorCode = e.getClass().getSimpleName();
            Instant retryAt = clock.instant().plus(properties.backoffFor(entry.getAttemptCount() + 1));
            boolean deadLettered = entry.markAttemptFailed(errorCode, retryAt, properties.maxAttempts());
            metrics.dispatchFailed();
            if (deadLettered) {
                metrics.deadLettered();
                log.error("[Audit] Outbox event dead-lettered after {} attempts eventId={} error={}",
                        entry.getAttemptCount(), entry.getEventId(), errorCode, e);
            } else {
                metrics.dispatchRetried();
                log.warn("[Audit] Outbox delivery failed, retry {} scheduled at {} eventId={} error={}",
                        entry.getAttemptCount(), retryAt, entry.getEventId(), errorCode);
            }
        }
    }

    /**
     * Returns rows whose worker died mid-flight to the queue. Without this a crash strands the event
     * in {@code PROCESSING} permanently, and the trail loses it as quietly as before this refactor.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reclaimExpiredLeases() {
        Instant now = clock.instant();
        int reclaimed = outboxRepository.reclaimExpiredLeases(now, now.minus(properties.leaseTimeout()));
        if (reclaimed > 0) {
            log.warn("[Audit] Reclaimed {} outbox rows whose worker lease had expired", reclaimed);
        }
        return reclaimed;
    }

    /**
     * Operator action: requeue dead-lettered rows after the cause is fixed. Deliberately never
     * automatic — a row reaches {@code FAILED} only after the full attempt budget was spent, so
     * retrying it on a timer just burns the same budget again.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int replayDeadLetters(int limit) {
        Instant now = clock.instant();
        List<AuditOutboxEntry> failed = outboxRepository.findByStatusOrderByCreatedAtAsc(
                AuditOutboxStatus.FAILED, PageRequest.ofSize(limit));
        failed.forEach(entry -> entry.replay(now));
        log.info("[Audit] Requeued {} dead-lettered audit events", failed.size());
        return failed.size();
    }

    /** Prunes drained rows only; {@code FAILED} rows are evidence and are never removed here. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int pruneProcessed() {
        return outboxRepository.deleteProcessedOlderThan(
                clock.instant().minus(properties.processedRetention()));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public long pendingCount() {
        return outboxRepository.countByStatus(AuditOutboxStatus.PENDING)
                + outboxRepository.countByStatus(AuditOutboxStatus.PROCESSING);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public long deadLetterCount() {
        return outboxRepository.countByStatus(AuditOutboxStatus.FAILED);
    }

    /** Age of the oldest event still waiting, in seconds; {@code 0} when the queue is drained. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public long oldestPendingAgeSeconds() {
        Instant oldest = outboxRepository.findOldestUnprocessedCreatedAt();
        return oldest == null ? 0L
                : Math.max(0L, clock.instant().getEpochSecond() - oldest.getEpochSecond());
    }
}
