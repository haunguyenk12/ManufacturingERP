package com.erp.manufacturing.common.audit.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One audit event waiting to be written to the immutable audit tables.
 *
 * <p>Unlike {@code AuditLog} this row <em>is</em> mutable — status, attempt count and lease all move
 * as the dispatcher works on it. That is the whole point of separating the two tables: the outbox is
 * a work queue and may be updated and eventually pruned, while {@code audit_logs} is append-only and
 * is protected against updates at the database level (AR-7).
 */
@Entity
@Table(name = "audit_outbox")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "outbox_id", updatable = false, nullable = false)
    private UUID outboxId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AuditOutboxStatus status = AuditOutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    @Builder.Default
    private Instant nextAttemptAt = Instant.now();

    /** Category, never a raw driver message — see the column comment in {@code V67}. */
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    /** Takes the row out of the queue under a lease held by {@code workerId}. */
    public void claim(String workerId, Instant now) {
        this.status = AuditOutboxStatus.PROCESSING;
        this.lockedAt = now;
        this.lockedBy = workerId;
    }

    public void markProcessed(Instant now) {
        this.status = AuditOutboxStatus.PROCESSED;
        this.processedAt = now;
        this.lockedAt = null;
        this.lockedBy = null;
        this.lastErrorCode = null;
    }

    /**
     * Schedules another attempt, or dead-letters once the cap is reached.
     *
     * @return {@code true} when this attempt exhausted the budget and the row is now {@code FAILED}
     */
    public boolean markAttemptFailed(String errorCode, Instant retryAt, int maxAttempts) {
        this.attemptCount++;
        this.lastErrorCode = errorCode;
        this.lockedAt = null;
        this.lockedBy = null;
        if (attemptCount >= maxAttempts) {
            this.status = AuditOutboxStatus.FAILED;
            return true;
        }
        this.status = AuditOutboxStatus.PENDING;
        this.nextAttemptAt = retryAt;
        return false;
    }

    /** Puts a dead-lettered row back in the queue after an operator has fixed the cause. */
    public void replay(Instant now) {
        this.status = AuditOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.lockedAt = null;
        this.lockedBy = null;
    }
}
