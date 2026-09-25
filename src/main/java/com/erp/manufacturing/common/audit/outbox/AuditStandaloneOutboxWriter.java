package com.erp.manufacturing.common.audit.outbox;

import com.erp.manufacturing.common.audit.AuditPayloadCodec;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an audit event into the outbox in a transaction of its own (AR-2).
 *
 * <p>Used for events that must survive the failure of the thing they describe: a rejected command, a
 * failed login, anything emitted outside a transaction at all. {@code REQUIRES_NEW} suspends the
 * caller's transaction, commits this row on its own connection, and resumes — so the failure record
 * is already durable by the time the business transaction rolls back.
 *
 * <p><strong>This has to be a separate bean.</strong> Spring applies {@code @Transactional} through a
 * proxy, and a call from {@link AuditOutboxWriter} to a {@code REQUIRES_NEW} method on itself would
 * bypass that proxy entirely: the annotation would be silently ignored, the insert would join the
 * doomed transaction, and every failure audit would vanish on rollback — the exact defect this
 * refactor exists to fix, reintroduced by a detail invisible at the call site.
 */
@Component
@RequiredArgsConstructor
public class AuditStandaloneOutboxWriter {

    private final AuditOutboxRepository outboxRepository;
    private final AuditPayloadCodec codec;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(AuditRecordDraft draft) {
        outboxRepository.save(AuditOutboxEntry.builder()
                .eventId(draft.eventId())
                .payload(codec.encode(draft))
                .status(AuditOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(draft.occurredAt())
                .occurredAt(draft.occurredAt())
                .build());
    }
}
