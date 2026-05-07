package com.erp.manufacturing.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AuditLogEvent event) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(event.context().userId())
                    .username(event.context().username())
                    .action(event.action())
                    .entityType(event.entityType())
                    .entityId(event.entityId())
                    .description(event.description())
                    .status(event.status())
                    .clientIp(event.context().clientIp())
                    .userAgent(event.context().userAgent())
                    .traceId(event.context().traceId())
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // NEVER let audit failure propagate to request thread
            log.error("[Audit] Failed to persist audit log: action={} traceId={}",
                    event.action(), event.context().traceId(), e);
        }
    }
}
