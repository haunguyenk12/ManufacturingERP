package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.model.AuditActorSnapshot;
import com.erp.manufacturing.common.audit.model.AuditOutcome;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.model.AuditRequestSnapshot;
import com.erp.manufacturing.common.audit.model.AuditScope;
import com.erp.manufacturing.common.audit.model.AuditSource;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxProperties;
import com.erp.manufacturing.common.context.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Compatibility facade over {@link AuditRecorder} (AR-2/AR-5).
 *
 * <p>Its signatures are unchanged so existing call sites keep compiling, but the behaviour underneath
 * is different in the way that matters: an event is now written to the transactional outbox instead
 * of being published as a Spring event for an {@code AFTER_COMMIT} listener to persist later.
 *
 * <p><strong>The bug this closes.</strong> {@code @TransactionalEventListener(AFTER_COMMIT)} only
 * delivers when the publishing thread is inside a transaction. {@code AuthService} carries no
 * {@code @Transactional} at all, so <em>every</em> auth event — login, failed login, lockout, token
 * reuse, session timeout, password reset — was published and then dropped on the floor, with no
 * error anywhere. The security-relevant half of the audit trail did not exist. Auth events now go
 * through the standalone writer, which needs no ambient transaction.
 *
 * <p><strong>Failure semantics.</strong> {@code logFailure} / {@code logAuthFailure} are written in
 * their own transaction, so they survive the rollback of the command they describe. Under the old
 * path a rejected command inside a transaction produced no record at all: the rollback took the
 * evidence with it.
 *
 * <p>When {@code app.audit.outbox.legacy-listener-enabled} is set, this publishes the old event
 * instead — the rollback lever from the staged rollout plan. The two paths are mutually exclusive by
 * construction ({@link AuditOutboxProperties} refuses to start with both on), because only the outbox
 * path carries the {@code eventId} that could deduplicate them.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final ApplicationEventPublisher eventPublisher;
    private final AuditRecorder auditRecorder;
    private final AuditOutboxProperties outboxProperties;

    // ── Standard request-scoped logging ──────────────────────────────────

    public void log(RequestContext ctx, AuditAction action) {
        log(ctx, action, null);
    }

    public void log(RequestContext ctx, AuditAction action, String description) {
        emit(ctx, action, null, null, null, description, AuditOutcome.SUCCESS,
                List.of(), AuditSource.HTTP, AuditScope.EMPTY, false);
    }

    public void logEntity(RequestContext ctx, AuditAction action,
                          String entityType, Object entityId) {
        logEntity(ctx, action, entityType, entityId, null, List.of());
    }

    public void logEntity(RequestContext ctx, AuditAction action,
                          String entityType, Object entityId, String entityName) {
        logEntity(ctx, action, entityType, entityId, entityName, List.of());
    }

    public void logEntity(RequestContext ctx, AuditAction action,
                          String entityType, Object entityId, String entityName,
                          List<AuditFieldChange> changes) {
        logEntity(ctx, action, entityType, entityId, entityName, changes, AuditScope.EMPTY);
    }

    /** Overload for call sites that know the organisational scope of what they just changed. */
    public void logEntity(RequestContext ctx, AuditAction action,
                          String entityType, Object entityId, String entityName,
                          List<AuditFieldChange> changes, AuditScope scope) {
        emit(ctx, action, entityType, entityId == null ? null : entityId.toString(), entityName,
                null, AuditOutcome.SUCCESS, changes, AuditSource.HTTP, scope, false);
    }

    /** Written in its own transaction so it outlives the rollback of the failed command. */
    public void logFailure(RequestContext ctx, AuditAction action, String reason) {
        emit(ctx, action, null, null, null, reason, AuditOutcome.FAILURE,
                List.of(), AuditSource.HTTP, AuditScope.EMPTY, true);
    }

    // ── Auth events (no transaction, often no principal yet) ─────────────

    public void logAuth(UUID userId, String username, String ip, String traceId,
                        AuditAction action, String description) {
        emit(RequestContext.forAuth(userId, username, ip, traceId), action,
                "User", userId == null ? null : userId.toString(), username,
                description, AuditOutcome.SUCCESS, List.of(), AuditSource.AUTH, AuditScope.EMPTY, true);
    }

    public void logAuthFailure(String username, String ip, String traceId,
                               AuditAction action, String reason) {
        emit(RequestContext.forAuth(null, username, ip, traceId), action,
                null, null, username, reason, AuditOutcome.FAILURE,
                List.of(), AuditSource.AUTH, AuditScope.EMPTY, true);
    }

    // ── Private ──────────────────────────────────────────────────────────

    private void emit(RequestContext ctx, AuditAction action,
                        String entityType, String entityId, String entityName,
                        String description, AuditOutcome outcome,
                        List<AuditFieldChange> changes, AuditSource source,
                        AuditScope scope, boolean standalone) {
        if (outboxProperties.legacyListenerEnabled()) {
            eventPublisher.publishEvent(new AuditLogEvent(ctx, action.name(), entityType, entityId,
                    entityName, description, outcome.name(),
                    changes == null ? List.of() : List.copyOf(changes)));
            return;
        }

        AuditRecordDraft draft = auditRecorder.draft(action)
                .outcome(outcome)
                .source(source)
                .scope(scope)
                .actor(actorOf(ctx))
                .request(requestOf(ctx))
                .description(description)
                // The action name doubles as the reason code for these legacy call sites: it is
                // already stable and machine-readable, which is the whole point of the column, and it
                // is a far better answer than putting a raw exception message on the wire.
                .reasonCode(outcome == AuditOutcome.FAILURE ? action.name() : null)
                .primaryEntity(entityType == null && entityId == null && entityName == null
                        ? null
                        : com.erp.manufacturing.common.audit.model.AuditEntityRef.primary(
                                entityType, entityId, entityName))
                .changes(changes == null ? List.of() : changes)
                .build();

        if (standalone) {
            auditRecorder.recordStandalone(draft);
        } else {
            auditRecorder.recordSuccess(draft);
        }
    }

    private AuditActorSnapshot actorOf(RequestContext ctx) {
        return new AuditActorSnapshot(ctx.userId(), ctx.username(),
                AuditActorSnapshot.AuditActorType.USER);
    }

    /**
     * Keeps the caller-supplied context as the authority. These call sites pass a context captured on
     * the request thread; re-resolving it here could pick up a different (or empty) one.
     */
    private AuditRequestSnapshot requestOf(RequestContext ctx) {
        AuditRequestSnapshot ambient = auditRecorder.currentContext().request();
        return new AuditRequestSnapshot(
                ctx.traceId() != null ? ctx.traceId() : ambient.traceId(),
                ctx.clientIp() != null ? ctx.clientIp() : ambient.clientIp(),
                ctx.userAgent() != null ? ctx.userAgent() : ambient.userAgent(),
                ambient.httpMethod(),
                ambient.requestPath());
    }
}
