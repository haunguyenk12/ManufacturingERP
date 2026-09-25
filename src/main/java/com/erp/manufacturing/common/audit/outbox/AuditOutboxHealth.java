package com.erp.manufacturing.common.audit.outbox;

import com.erp.manufacturing.common.audit.AuditMetrics;
import io.micrometer.core.instrument.Gauge;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Backlog visibility for the audit outbox (AR-9).
 *
 * <p>The pipeline is asynchronous, which means a total failure to record anything looks exactly like
 * a quiet day: no errors, no missing responses, nothing to notice. These three gauges are the
 * difference between "the audit trail stopped four hours ago" being discovered by monitoring and
 * being discovered by whoever needed the trail.
 *
 * <ul>
 *   <li>{@code audit_outbox_pending_total} — depth of the queue right now.</li>
 *   <li>{@code audit_outbox_oldest_pending_age_seconds} — the one to alert on. Depth alone is
 *       ambiguous: a large but fast-moving queue is healthy, while a single row stuck for an hour is
 *       not, and only age tells those apart.</li>
 *   <li>{@code audit_dead_letter_total} — events the dispatcher gave up on. Any non-zero value is a
 *       hole in the trail and needs a human, which is why these rows are never auto-deleted.</li>
 * </ul>
 *
 * <p>The health indicator reports {@code DOWN} only for dead letters (evidence is already missing)
 * and degraded-but-{@code UP} for a backlog past its SLO, since a slow drain still ends with every
 * event recorded.
 */
@Component
@RequiredArgsConstructor
public class AuditOutboxHealth implements HealthIndicator {

    private final AuditOutboxProcessor processor;
    private final AuditOutboxProperties properties;
    private final AuditMetrics metrics;

    @PostConstruct
    void registerGauges() {
        Gauge.builder("audit_outbox_pending_total", processor, AuditOutboxProcessor::pendingCount)
                .description("Audit events written but not yet materialised")
                .register(metrics.registry());
        Gauge.builder("audit_outbox_oldest_pending_age_seconds", processor,
                        AuditOutboxProcessor::oldestPendingAgeSeconds)
                .description("Age of the oldest audit event still waiting to be materialised")
                .register(metrics.registry());
        Gauge.builder("audit_dead_letter_total", processor, AuditOutboxProcessor::deadLetterCount)
                .description("Audit events the dispatcher gave up on; each one is a gap in the trail")
                .register(metrics.registry());
    }

    @Override
    public Health health() {
        long deadLetters = processor.deadLetterCount();
        long pending = processor.pendingCount();
        long oldestAgeSeconds = processor.oldestPendingAgeSeconds();
        long sloSeconds = properties.pendingAgeSlo().toSeconds();

        Health.Builder builder = deadLetters > 0 ? Health.down() : Health.up();
        return builder
                .withDetail("pending", pending)
                .withDetail("deadLetters", deadLetters)
                .withDetail("oldestPendingAgeSeconds", oldestAgeSeconds)
                .withDetail("pendingAgeSloSeconds", sloSeconds)
                .withDetail("backlogWithinSlo", oldestAgeSeconds <= sloSeconds)
                .withDetail("dispatcherEnabled", properties.dispatcherEnabled())
                .build();
    }
}
