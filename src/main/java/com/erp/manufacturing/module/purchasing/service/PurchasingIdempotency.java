package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.purchasing.domain.PurchasingCommand;
import com.erp.manufacturing.module.purchasing.domain.PurchasingIdempotencyRecord;
import com.erp.manufacturing.module.purchasing.repository.PurchasingIdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * G7a: {@code Idempotency-Key} for purchasing commands. The header is optional — without it a
 * command runs exactly as before. With it, a replay of the same key and payload returns the
 * document's <em>current</em> state without running again; the same key with another payload is
 * {@code IDEMPOTENCY_CONFLICT} (409). The fingerprint includes the target document, so reusing a key
 * on a different order is a conflict too.
 *
 * <p>Authorisation is not re-implemented here: every caller is a {@code @PreAuthorize} method, so a
 * replay is re-authorised with the caller's current assignments before it reaches this class.
 */
@Component
@RequiredArgsConstructor
public class PurchasingIdempotency {

    private final PurchasingIdempotencyRecordRepository repository;
    private final IdempotencySupport idempotency;

    /** Normalised key, or {@code null} when the header was not sent. */
    public String key(String rawKey) {
        return StringUtils.hasText(rawKey) ? idempotency.normalizeKey(rawKey) : null;
    }

    /**
     * The document a previous use of this key ran on, after checking the payload is the same.
     * Empty when there is no key or the key is new.
     */
    public Optional<UUID> replay(String key, PurchasingCommand command, Object fingerprint) {
        if (key == null) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKeyAndCommand(key, command).map(record -> {
            idempotency.ensureSamePayload(record.getPayloadHash(), fingerprint);
            return record.getResourceId();
        });
    }

    /**
     * Claims the key inside the command's transaction. Flushed at once so a concurrent request with
     * the same key stops on {@code uk_purchasing_idempotency_key_command} instead of running in full.
     */
    public void record(String key, PurchasingCommand command, UUID resourceId, Object fingerprint) {
        if (key == null) {
            return;
        }
        repository.saveAndFlush(PurchasingIdempotencyRecord.builder()
                .idempotencyKey(key)
                .command(command)
                .resourceId(resourceId)
                .payloadHash(idempotency.payloadHash(fingerprint))
                .build());
    }

    /** What a command's key fingerprints: the target document plus the body. */
    public record Fingerprint(UUID resourceId, Object body) {}
}
