package com.erp.manufacturing.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * <strong>Legacy path, disabled by default</strong> — superseded by the transactional outbox
 * ({@code AuditRecorder} → {@code AuditOutboxWriter} → {@code AuditOutboxDispatcher}). Kept only as
 * the rollback lever described in AuditRefactorPlan §8.2, behind
 * {@code app.audit.outbox.legacy-listener-enabled}, and scheduled for removal once the new pipeline
 * has been stable for a release (AR-10).
 *
 * <p>Its two structural defects are the reason the outbox exists, and are worth stating so nobody
 * re-enables it casually:
 *
 * <ol>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)} delivers <em>only</em> when the publisher
 *       is inside a transaction. {@code AuthService} has none, so every auth event was silently
 *       discarded — no log line, no metric, nothing.</li>
 *   <li>Between the business COMMIT and this INSERT there is a window with no durability guarantee at
 *       all: a crash, an executor rejection or a failing INSERT loses the event while the business
 *       change survives.</li>
 * </ol>
 *
 * Uses the dedicated "auditExecutor" thread pool with MDC propagation.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.audit.outbox.legacy-listener-enabled", havingValue = "true")
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogListener {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogChangeRepository auditLogChangeRepository;

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AuditLogEvent event) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(event.context().userId())
                    .username(event.context().username())
                    .action(event.action())
                    .entityType(event.entityType())
                    .entityId(event.entityId())
                    .entityName(event.entityName())
                    .description(event.description())
                    .status(event.status())
                    .clientIp(event.context().clientIp())
                    .userAgent(event.context().userAgent())
                    .traceId(event.context().traceId())
                    .build();
            AuditLog saved = auditLogRepository.saveAndFlush(entry);
            if (event.changes() != null && !event.changes().isEmpty()) {
                List<AuditLogChange> changes = event.changes().stream()
                        .map(change -> AuditLogChange.builder()
                                .auditId(saved.getAuditId())
                                .fieldName(change.fieldName())
                                .oldValue(change.oldValue())
                                .newValue(change.newValue())
                                .changeType(change.changeType())
                                .build())
                        .toList();
                auditLogChangeRepository.saveAll(changes);
            }
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            // NEVER let audit failure propagate to request thread
            log.error("[Audit] Failed to persist audit log: action={} traceId={}",
                    event.action(), event.context().traceId(), e);
        }
    }
}
