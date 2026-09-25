package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.context.AuditContext;
import com.erp.manufacturing.common.audit.context.AuditContextResolver;
import com.erp.manufacturing.common.audit.model.AuditEntityRef;
import com.erp.manufacturing.common.audit.model.AuditOutcome;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.model.AuditRequestSnapshot;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxProperties;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxWriter;
import com.erp.manufacturing.common.audit.outbox.AuditStandaloneOutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The one entry point business code uses to record an audit event (AR-2/AR-3).
 *
 * <p>It does three things and nothing else: fills the ambient context the caller did not supply,
 * runs every value through {@link AuditInputSanitizer}, and hands the result to the right outbox
 * writer — the transactional one for outcomes that must not outlive a rollback, the standalone one
 * for outcomes that must.
 *
 * <p><strong>Failure policy: FAIL_OPEN, system-wide</strong> (decision §11.1). A problem recording an
 * audit event never changes the business response. The honest caveat, stated here rather than
 * discovered later: on the success path the outbox insert is deliberately part of the business
 * transaction, so if the database itself rejects that insert, catching the exception here does not
 * un-poison the transaction and the business change will still roll back. That is the price of
 * atomicity, and it is why {@link AuditInputSanitizer} caps every value <em>before</em> this point —
 * so the only remaining way for that insert to fail is a database outage that would have failed the
 * business write anyway. Everything that is merely an audit-side defect (an unreadable expression, an
 * oversized payload, a missing context) is absorbed here and counted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditRecorder {

    private final AuditContextResolver contextResolver;
    private final AuditOutboxWriter outboxWriter;
    private final AuditStandaloneOutboxWriter standaloneOutboxWriter;
    private final AuditInputSanitizer sanitizer;
    private final AuditOutboxProperties properties;
    private final AuditMetrics metrics;
    private final Clock clock;

    /** Ambient context for the current thread; callers building a draft by hand start here. */
    public AuditContext currentContext() {
        return contextResolver.resolve();
    }

    /**
     * A draft pre-filled from the ambient context, with the timestamp taken from the injected
     * {@link Clock} so tests can pin it.
     */
    public AuditRecordDraft.Builder draft(AuditAction action) {
        AuditContext context = currentContext();
        return AuditRecordDraft.builder()
                .action(action)
                .occurredAt(clock.instant())
                .actor(context.actor())
                .request(context.request())
                .scope(context.scope())
                .source(context.source());
    }

    /**
     * Records a successful mutation. The outbox row joins the caller's transaction, so it commits
     * with the change it describes or not at all.
     */
    public void recordSuccess(AuditRecordDraft draft) {
        write(draft, AuditOutcome.SUCCESS, false);
    }

    /**
     * Records a rejected command. Written in its own transaction so it survives the rollback of the
     * business transaction that is about to be undone.
     */
    public void recordFailure(AuditRecordDraft draft) {
        write(draft, AuditOutcome.FAILURE, true);
    }

    /**
     * Records an event that has no business transaction to join — an auth outcome, a scheduled job, a
     * filter-level event. Always its own transaction.
     */
    public void recordStandalone(AuditRecordDraft draft) {
        write(draft, draft.outcome(), true);
    }

    private void write(AuditRecordDraft draft, AuditOutcome outcome, boolean standalone) {
        if (!properties.producerEnabled()) {
            return;
        }
        try {
            AuditRecordDraft sanitized = sanitize(draft, outcome);
            if (standalone) {
                standaloneOutboxWriter.append(sanitized);
            } else {
                outboxWriter.append(sanitized);
            }
        } catch (Exception e) {
            metrics.recordFailed(draft.source().name());
            // Category only. Echoing the exception message risks quoting the very row that failed,
            // which is how a secret ends up in a log line that is safe to read by definition.
            log.error("[Audit] Could not enqueue audit event action={} eventId={} source={} error={}",
                    draft.action(), draft.eventId(), draft.source(), e.getClass().getSimpleName(), e);
        }
    }

    /**
     * The single place where every value is capped before it can reach a column. Applied to the whole
     * draft rather than at each producer, so a new producer cannot forget it.
     */
    private AuditRecordDraft sanitize(AuditRecordDraft draft, AuditOutcome outcome) {
        AuditRequestSnapshot request = draft.request();
        List<AuditEntityRef> entities = new ArrayList<>();
        for (AuditEntityRef ref : draft.entities()) {
            AuditEntityRef capped = new AuditEntityRef(ref.relation(),
                    sanitizer.entityType(ref.entityType()),
                    sanitizer.entityId(ref.entityId()),
                    sanitizer.entityName(ref.entityName()));
            if (!capped.isEmpty()) {
                entities.add(capped);
            }
        }

        List<AuditFieldChange> changes = draft.changes().stream()
                .filter(change -> !sanitizer.isSensitiveFieldName(change.fieldName()) || dropSensitive())
                .map(change -> new AuditFieldChange(
                        sanitizer.fieldName(change.fieldName()),
                        sanitizer.jsonValue(change.fieldName(), change.oldValue()),
                        sanitizer.jsonValue(change.fieldName(), change.newValue()),
                        change.changeType()))
                .filter(change -> change.fieldName() != null)
                .toList();

        return new AuditRecordDraft(
                draft.eventId(),
                draft.occurredAt(),
                draft.action(),
                outcome,
                draft.actor().userId() == null && draft.actor().username() == null
                        ? draft.actor()
                        : new com.erp.manufacturing.common.audit.model.AuditActorSnapshot(
                                draft.actor().userId(),
                                sanitizer.username(draft.actor().username()),
                                draft.actor().actorType()),
                entities,
                draft.scope(),
                draft.source(),
                new AuditRequestSnapshot(
                        sanitizer.traceId(request.traceId()),
                        sanitizer.clientIp(request.clientIp()),
                        sanitizer.userAgent(request.userAgent()),
                        sanitizer.httpMethod(request.httpMethod()),
                        sanitizer.requestPath(request.requestPath())),
                sanitizer.reasonCode(draft.reasonCode()),
                sanitizer.description(draft.description()),
                changes,
                draft.metadataJson());
    }

    /** Always false: a sensitive field is dropped, never stored. Counted so the drop is visible. */
    private boolean dropSensitive() {
        sanitizer.countSensitiveDrop();
        return false;
    }
}
