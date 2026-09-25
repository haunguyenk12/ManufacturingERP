package com.erp.manufacturing.common.audit.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning and rollout switches for the audit outbox ({@code app.audit.outbox.*}).
 *
 * <p>The three enable flags exist for the staged rollout in AuditRefactorPlan §8.1: additive schema
 * first, dispatcher second (idle, because nothing produces yet), producer third. They are also the
 * rollback lever — the dispatcher can be stopped without losing anything, because unprocessed rows
 * simply stay in the table.
 *
 * <p><strong>{@code legacyListenerEnabled} defaults to {@code false} and must stay off while the
 * producer is on.</strong> Both paths would otherwise write the same logical event through two
 * different identities, and the {@code event_id} unique index cannot deduplicate what the legacy
 * path never assigns.
 *
 * @param producerEnabled    write audit events into the outbox
 * @param dispatcherEnabled  drain the outbox into the immutable audit tables
 * @param legacyListenerEnabled keep the pre-refactor AFTER_COMMIT listener alive
 * @param batchSize          rows claimed per dispatcher pass
 * @param maxAttempts        attempts before a row is dead-lettered
 * @param initialBackoff     first retry delay; doubled per attempt up to {@code maxBackoff}
 * @param maxBackoff         ceiling for the exponential backoff
 * @param leaseTimeout       how long a claimed row may stay PROCESSING before it is reclaimed
 * @param pendingAgeSlo      backlog age past which the health indicator reports degraded
 * @param processedRetention how long drained PROCESSED rows are kept before pruning
 */
@ConfigurationProperties(prefix = "app.audit.outbox")
public record AuditOutboxProperties(
        Boolean producerEnabled,
        Boolean dispatcherEnabled,
        Boolean legacyListenerEnabled,
        Integer batchSize,
        Integer maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        Duration leaseTimeout,
        Duration pendingAgeSlo,
        Duration processedRetention
) {

    public AuditOutboxProperties {
        producerEnabled = producerEnabled == null || producerEnabled;
        dispatcherEnabled = dispatcherEnabled == null || dispatcherEnabled;
        legacyListenerEnabled = legacyListenerEnabled != null && legacyListenerEnabled;
        batchSize = batchSize == null || batchSize < 1 ? 100 : batchSize;
        maxAttempts = maxAttempts == null || maxAttempts < 1 ? 8 : maxAttempts;
        initialBackoff = initialBackoff == null ? Duration.ofSeconds(2) : initialBackoff;
        maxBackoff = maxBackoff == null ? Duration.ofMinutes(5) : maxBackoff;
        leaseTimeout = leaseTimeout == null ? Duration.ofMinutes(2) : leaseTimeout;
        pendingAgeSlo = pendingAgeSlo == null ? Duration.ofSeconds(60) : pendingAgeSlo;
        processedRetention = processedRetention == null ? Duration.ofDays(3) : processedRetention;

        if (producerEnabled && legacyListenerEnabled) {
            throw new IllegalStateException(
                    "app.audit.outbox: producer-enabled and legacy-listener-enabled must not both be true — "
                            + "the two paths write the same logical event twice and only the outbox path "
                            + "carries the event_id that could deduplicate them");
        }
    }

    /** Backoff for the attempt that has just failed, capped at {@link #maxBackoff()}. */
    public Duration backoffFor(int attemptCount) {
        long millis = initialBackoff.toMillis();
        for (int i = 1; i < attemptCount && millis < maxBackoff.toMillis(); i++) {
            millis *= 2;
        }
        return Duration.ofMillis(Math.min(millis, maxBackoff.toMillis()));
    }
}
