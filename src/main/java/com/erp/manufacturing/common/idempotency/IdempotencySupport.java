package com.erp.manufacturing.common.idempotency;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Shared {@code Idempotency-Key} handling. Replaces three byte-identical private copies that
 * previously lived in {@code WorkOrderExecutionSupport}, {@code InventoryMovementService} and
 * {@code GoodsReceiptService}.
 *
 * <p>Two responsibilities:
 * <ul>
 *   <li><b>Key normalisation</b> – trim + length guard. Missing key is a client error, not a silent pass.</li>
 *   <li><b>Payload fingerprinting</b> – replaying a key with a <em>different</em> body used to
 *       silently return the original document and discard the new payload. It now raises
 *       {@link BusinessErrorCode#IDEMPOTENCY_CONFLICT} (409) as required by the frontend contract.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class IdempotencySupport {

    public static final int KEY_MAX_LENGTH = 120;

    /** Child keys append {@code :L<index>}; the prefix is clipped so the result still fits the column. */
    private static final int CHILD_KEY_PREFIX_MAX_LENGTH = 112;

    private final ObjectMapper objectMapper;

    public String normalizeKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "Idempotency-Key header is required");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > KEY_MAX_LENGTH) {
            throw ExceptionFactory.custom(ValidationErrorCode.FIELD_TOO_LONG,
                    "Idempotency-Key must be at most " + KEY_MAX_LENGTH + " characters");
        }
        return normalized;
    }

    public String childKey(String parentKey, int index) {
        String prefix = parentKey.length() > CHILD_KEY_PREFIX_MAX_LENGTH
                ? parentKey.substring(0, CHILD_KEY_PREFIX_MAX_LENGTH)
                : parentKey;
        return prefix + ":L" + index;
    }

    /** SHA-256 (hex) of the payload's JSON form. Records serialise in declaration order, so this is stable. */
    public String payloadHash(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to fingerprint idempotent payload", e);
        }
    }

    /**
     * Guards a replay. A {@code null} {@code storedHash} means the document predates payload
     * fingerprinting (or was written by an internally-derived child key), so the replay is allowed
     * through unchanged — existing rows must not start failing.
     *
     * @throws com.erp.manufacturing.common.exception.AppException 409 when the same key is replayed with a different payload
     */
    public void ensureSamePayload(String storedHash, Object payload) {
        if (storedHash == null) {
            return;
        }
        if (!storedHash.equals(payloadHash(payload))) {
            throw ExceptionFactory.custom(BusinessErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key was already used with a different payload");
        }
    }
}
