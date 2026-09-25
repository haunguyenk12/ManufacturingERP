package com.erp.manufacturing.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * The single boundary where every value is normalised and capped before it can reach an audit row
 * (AR-1).
 *
 * <p><strong>Why this class exists.</strong> Two of the columns an audit row is built from are under
 * the caller's control: {@code X-Trace-Id} (accepted up to 64 characters by {@code TraceIdFilter})
 * and {@code User-Agent} (accepted up to the servlet container's header limit, kilobytes). The
 * columns behind them were {@code VARCHAR(32)} and {@code VARCHAR(512)}. A long header therefore made
 * the audit INSERT fail <em>after</em> the business transaction had already committed: the business
 * change survived, the record of who made it did not, and the only trace was an ERROR log line. Any
 * client could suppress its own audit trail with one header, so the cap has to be applied here,
 * deliberately, instead of being discovered from a database exception.
 *
 * <p><strong>Truncation rules.</strong> Text is cut on a code-point boundary, never mid-surrogate, so
 * a value ending in an emoji cannot produce an unpaired surrogate that Postgres rejects with
 * {@code invalid byte sequence for encoding "UTF8"} — which would trade one insert failure for
 * another. Oversized JSON is <em>not</em> cut at all: a truncated JSON document is not JSON and the
 * column is {@code jsonb}. It is replaced by a marker document carrying the original size and a
 * SHA-256 of the content, so the event still records that the field changed and the value stays
 * verifiable against an external copy without being stored.
 */
@Component
@Slf4j
public class AuditInputSanitizer {

    public static final int TRACE_ID_MAX     = 64;
    public static final int USERNAME_MAX     = 100;
    public static final int ACTION_MAX       = 100;
    public static final int ENTITY_TYPE_MAX  = 100;
    public static final int ENTITY_ID_MAX    = 255;
    public static final int ENTITY_NAME_MAX  = 255;
    public static final int CLIENT_IP_MAX    = 45;
    public static final int USER_AGENT_MAX   = 512;
    public static final int REASON_CODE_MAX  = 100;
    public static final int HTTP_METHOD_MAX  = 10;
    public static final int REQUEST_PATH_MAX = 512;
    public static final int FIELD_NAME_MAX   = 150;
    public static final int DESCRIPTION_MAX  = 4000;

    /** Ceiling for one {@code jsonb} snapshot value. */
    public static final int JSON_VALUE_MAX_BYTES = 8 * 1024;
    /** Ceiling for the whole allow-listed metadata document. */
    public static final int METADATA_MAX_BYTES  = 16 * 1024;
    public static final int METADATA_MAX_FIELDS = 50;
    public static final int METADATA_MAX_DEPTH  = 4;

    /**
     * Safety net for values nobody has annotated with {@link AuditSensitive} yet. Kept as a net, not
     * as the policy — see that annotation's javadoc.
     */
    private static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "password", "token", "secret", "credential", "authorization", "hash", "apikey", "api_key");

    private final ObjectMapper objectMapper;
    private final AuditMetrics metrics;

    public AuditInputSanitizer(ObjectMapper objectMapper, AuditMetrics metrics) {
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    // Text -----------------------------------------------------------------

    public String traceId(String value)     { return text(value, TRACE_ID_MAX); }
    public String username(String value)    { return text(value, USERNAME_MAX); }
    public String action(String value)      { return text(value, ACTION_MAX); }
    public String entityType(String value)  { return text(value, ENTITY_TYPE_MAX); }
    public String entityId(String value)    { return text(value, ENTITY_ID_MAX); }
    public String entityName(String value)  { return text(value, ENTITY_NAME_MAX); }
    public String clientIp(String value)    { return text(value, CLIENT_IP_MAX); }
    public String userAgent(String value)   { return text(value, USER_AGENT_MAX); }
    public String reasonCode(String value)  { return text(value, REASON_CODE_MAX); }
    public String httpMethod(String value)  { return text(value, HTTP_METHOD_MAX); }
    public String requestPath(String value) { return text(value, REQUEST_PATH_MAX); }
    public String fieldName(String value)   { return text(value, FIELD_NAME_MAX); }
    public String description(String value) { return text(value, DESCRIPTION_MAX); }

    /**
     * Trims, collapses control characters (a {@code User-Agent} carrying a newline would otherwise
     * forge log lines), drops blanks to {@code null} and caps the length on a code-point boundary.
     */
    public String text(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = stripControlCharacters(value).trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return truncateOnCodePointBoundary(cleaned, maxLength);
    }

    /**
     * Cuts to at most {@code maxLength} {@code char}s without ever leaving a lone surrogate behind.
     * {@link String#substring} counts UTF-16 units, so cutting inside a surrogate pair yields a
     * string that is no longer valid UTF-8 once encoded.
     */
    public static String truncateOnCodePointBoundary(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    // Sensitivity ----------------------------------------------------------

    /** {@code true} when a field must never be captured, judged by name alone. */
    public boolean isSensitiveFieldName(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return SENSITIVE_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    public void countSensitiveDrop() {
        metrics.sensitiveFieldDropped();
    }

    // JSON snapshot values -------------------------------------------------

    /**
     * Caps one {@code jsonb} snapshot. Oversized content is replaced, never cut, so the parent audit
     * event survives whole instead of being lost to an insert failure.
     */
    public String jsonValue(String fieldName, String json) {
        if (json == null) {
            return null;
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= JSON_VALUE_MAX_BYTES) {
            return json;
        }
        metrics.payloadTruncated();
        log.warn("[Audit] Snapshot for field '{}' is {} bytes (max {}); stored as a truncation marker",
                fieldName, bytes.length, JSON_VALUE_MAX_BYTES);
        ObjectNode marker = objectMapper.createObjectNode();
        marker.put("_audit", "TRUNCATED");
        marker.put("bytes", bytes.length);
        marker.put("sha256", sha256Hex(bytes));
        return marker.toString();
    }

    /**
     * Validates a manually supplied metadata document against the depth / field-count / size quota.
     * Over-quota metadata is dropped to {@code null} rather than partially stored: half a metadata
     * document reads exactly like a complete one and would mislead whoever investigates the event.
     */
    public String metadata(JsonNode metadata) {
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            return null;
        }
        if (!metadata.isObject()) {
            log.warn("[Audit] Metadata must be a JSON object; dropped a {}", metadata.getNodeType());
            return null;
        }
        int depth = depth(metadata);
        if (metadata.size() > METADATA_MAX_FIELDS || depth > METADATA_MAX_DEPTH) {
            metrics.payloadTruncated();
            log.warn("[Audit] Metadata exceeds the structural quota (fields={}, depth={}); dropped",
                    metadata.size(), depth);
            return null;
        }
        String json = metadata.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > METADATA_MAX_BYTES) {
            metrics.payloadTruncated();
            log.warn("[Audit] Metadata exceeds {} bytes; dropped", METADATA_MAX_BYTES);
            return null;
        }
        return json;
    }

    private int depth(JsonNode node) {
        if (!node.isContainerNode()) {
            return 0;
        }
        int deepest = 0;
        for (JsonNode child : node) {
            deepest = Math.max(deepest, depth(child));
        }
        return deepest + 1;
    }

    public static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    private static String stripControlCharacters(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
                builder.append(' ');
            } else if (!Character.isISOControl(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
        });
        return builder.toString();
    }
}
