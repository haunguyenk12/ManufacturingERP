package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCommand;
import com.erp.manufacturing.module.workorder.domain.WorkOrderIdempotencyRecord;
import com.erp.manufacturing.module.workorder.repository.WorkOrderIdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * G8: {@code Idempotency-Key} for work order commands — the same contract as
 * {@code PurchasingIdempotency} (G7a). The header is optional; without it a command runs exactly as
 * before. With it, a replay of the same key and payload returns the resource's <em>current</em> state
 * without running again, and the same key with another payload is {@code IDEMPOTENCY_CONFLICT} (409).
 * The fingerprint includes the target work order, so reusing a key on another one conflicts too.
 *
 * <p>A replay is checked <b>before</b> {@code expectedWorkOrderVersion}: the first run moved the
 * version on, so the retried body is always one version behind and would otherwise be answered
 * {@code CONCURRENT_MODIFICATION} instead of replayed. Authorisation is not re-implemented here —
 * every caller is a {@code @PreAuthorize} method, so a replay is re-authorised first.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderIdempotency {

    private final WorkOrderIdempotencyRecordRepository repository;
    private final IdempotencySupport idempotency;

    /** Normalised key, or {@code null} when the header was not sent. */
    public String key(String rawKey) {
        return StringUtils.hasText(rawKey) ? idempotency.normalizeKey(rawKey) : null;
    }

    /**
     * G11: normalised key of a command that requires the header (FE §3.4, like the commercial commands of G10);
     * {@code MISSING_REQUIRED_FIELD} when it was not sent.
     */
    public String requiredKey(String rawKey) {
        return idempotency.normalizeKey(rawKey);
    }

    /** The resource a previous use of this key ran on, after checking the payload is the same. */
    public Optional<UUID> replay(String key, WorkOrderCommand command, Object fingerprint) {
        if (key == null) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKeyAndCommand(key, command).map(record -> {
            idempotency.ensureSamePayload(record.getPayloadHash(), fingerprint);
            return record.getResourceId();
        });
    }

    /**
     * Claims the key inside the command's transaction, flushed at once so a concurrent request with the
     * same key stops on {@code uk_work_order_idempotency_key_command} instead of running in full. A
     * command that fails rolls the claim back with it.
     */
    public void record(String key, WorkOrderCommand command, UUID resourceId, Object fingerprint) {
        if (key == null) {
            return;
        }
        repository.saveAndFlush(WorkOrderIdempotencyRecord.builder()
                .idempotencyKey(key)
                .command(command)
                .resourceId(resourceId)
                .payloadHash(idempotency.payloadHash(fingerprint))
                .build());
    }

    /** What a command's key fingerprints: the target work order plus the body. */
    public record Fingerprint(UUID workOrderId, Object body) {}
}
