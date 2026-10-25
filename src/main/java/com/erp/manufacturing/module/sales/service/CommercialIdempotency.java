package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.sales.domain.CommercialCommand;
import com.erp.manufacturing.module.sales.domain.CommercialIdempotencyRecord;
import com.erp.manufacturing.module.sales.repository.CommercialIdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * G10 (D9): {@code Idempotency-Key} for every GĐ10 command — the {@code WorkOrderIdempotency} contract with one
 * difference: the header is <b>required</b> (FE §3.4; these endpoints have no older clients to keep working). A
 * replay of the same key and payload returns the document's current state without running again; the same key
 * with another payload or target is {@code IDEMPOTENCY_CONFLICT} (409).
 *
 * <p>A replay is checked <b>before</b> any version: the first run moved the version on, so the retried body is
 * always one version behind. Authorisation is not re-implemented here — every caller is a {@code @PreAuthorize}
 * method, so a replay is re-authorised against the caller's current permissions first (FE R20).
 */
@Component
@RequiredArgsConstructor
public class CommercialIdempotency {

    private final CommercialIdempotencyRecordRepository repository;
    private final IdempotencySupport idempotency;

    /** Normalised key; {@code MISSING_REQUIRED_FIELD} when the header was not sent. */
    public String key(String rawKey) {
        return idempotency.normalizeKey(rawKey);
    }

    /** The document a previous use of this key ran on, after checking the payload is the same. */
    public Optional<UUID> replay(String key, CommercialCommand command, Object fingerprint) {
        return repository.findByIdempotencyKeyAndCommand(key, command).map(record -> {
            idempotency.ensureSamePayload(record.getPayloadHash(), fingerprint);
            return record.getResourceId();
        });
    }

    /**
     * Claims the key inside the command's transaction, flushed at once so a concurrent request with the same key
     * stops on {@code uk_commercial_idempotency_key_command} instead of running in full.
     */
    public void record(String key, CommercialCommand command, UUID resourceId, Object fingerprint) {
        repository.saveAndFlush(CommercialIdempotencyRecord.builder()
                .idempotencyKey(key)
                .command(command)
                .resourceId(resourceId)
                .payloadHash(idempotency.payloadHash(fingerprint))
                .build());
    }

    /** What a command's key fingerprints: the target document (null on create) plus the body. */
    public record Fingerprint(UUID targetId, Object body) {}
}
