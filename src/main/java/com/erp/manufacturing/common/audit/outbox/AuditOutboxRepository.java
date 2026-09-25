package com.erp.manufacturing.common.audit.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditOutboxRepository extends JpaRepository<AuditOutboxEntry, UUID> {

    /**
     * Claims a batch of due rows for this worker.
     *
     * <p>{@code PESSIMISTIC_WRITE} plus {@code jakarta.persistence.lock.timeout = -2}
     * ({@code SKIP LOCKED}) is what lets several application instances drain the same outbox without
     * either duplicating work or serialising behind each other: a row another worker already holds is
     * skipped rather than waited on. Without {@code SKIP LOCKED} a second instance blocks on the
     * first one's batch, and the queue drains at single-worker speed no matter how many instances run.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT e FROM AuditOutboxEntry e
            WHERE e.status = com.erp.manufacturing.common.audit.outbox.AuditOutboxStatus.PENDING
              AND e.nextAttemptAt <= :now
            ORDER BY e.createdAt ASC
            """)
    List<AuditOutboxEntry> claimDueBatch(@Param("now") Instant now, Pageable pageable);

    /**
     * Rows whose worker died mid-flight. Reclaimed on lease expiry, otherwise a crash would strand
     * the event in {@code PROCESSING} for good and the trail would silently lose it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AuditOutboxEntry e
               SET e.status = com.erp.manufacturing.common.audit.outbox.AuditOutboxStatus.PENDING,
                   e.lockedAt = NULL,
                   e.lockedBy = NULL,
                   e.nextAttemptAt = :now
             WHERE e.status = com.erp.manufacturing.common.audit.outbox.AuditOutboxStatus.PROCESSING
               AND e.lockedAt < :leaseExpiredBefore
            """)
    int reclaimExpiredLeases(@Param("now") Instant now,
                             @Param("leaseExpiredBefore") Instant leaseExpiredBefore);

    long countByStatus(AuditOutboxStatus status);

    @Query("""
            SELECT MIN(e.createdAt) FROM AuditOutboxEntry e
            WHERE e.status <> com.erp.manufacturing.common.audit.outbox.AuditOutboxStatus.PROCESSED
            """)
    Instant findOldestUnprocessedCreatedAt();

    List<AuditOutboxEntry> findByStatusOrderByCreatedAtAsc(AuditOutboxStatus status, Pageable pageable);

    Optional<AuditOutboxEntry> findByEventId(UUID eventId);

    /**
     * Prunes drained rows. Restricted to {@code PROCESSED} on purpose: the audit row it produced is
     * already durable, so nothing is lost. {@code FAILED} rows are never touched here.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM AuditOutboxEntry e
            WHERE e.status = com.erp.manufacturing.common.audit.outbox.AuditOutboxStatus.PROCESSED
              AND e.processedAt < :olderThan
            """)
    int deleteProcessedOlderThan(@Param("olderThan") Instant olderThan);
}
