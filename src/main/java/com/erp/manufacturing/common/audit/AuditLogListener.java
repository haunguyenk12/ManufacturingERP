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
 * Listens for {@link AuditLogEvent} and persists to DB asynchronously.
 * <p>
 * Uses {@code @TransactionalEventListener(AFTER_COMMIT)} to ensure:
 * <ul>
 *   <li>Audit is only written if the business transaction succeeded</li>
 *   <li>Rollbacks do NOT produce audit entries</li>
 * </ul>
 * Uses dedicated "auditExecutor" thread pool with MDC propagation.
 */
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
