package com.erp.manufacturing.common.audit.outbox;

import com.erp.manufacturing.common.audit.AuditPayloadCodec;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an audit event into the outbox <strong>inside the caller's transaction</strong> (AR-2).
 *
 * <p>{@code REQUIRED} is the entire design, not a default left unset. Joining the business
 * transaction is what makes the two facts inseparable: if the business change commits, the outbox row
 * commits with it and the event can no longer be lost; if the business change rolls back, the outbox
 * row rolls back too and no {@code SUCCESS} audit can survive an operation that never happened. The
 * old {@code AFTER_COMMIT} listener could only ever guarantee the second half.
 */
@Component
@RequiredArgsConstructor
public class AuditOutboxWriter {

    private final AuditOutboxRepository outboxRepository;
    private final AuditPayloadCodec codec;

    @Transactional(propagation = Propagation.REQUIRED)
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
