package com.erp.manufacturing.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditInputSanitizer")
class AuditInputSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuditInputSanitizer sanitizer = new AuditInputSanitizer(
            objectMapper, new AuditMetrics(new SimpleMeterRegistry()));

    @Test
    @DisplayName("caps a client-supplied trace id to the column width instead of letting the insert fail")
    void traceId_isCappedToTheColumnWidth() {
        // TraceIdFilter accepts up to 64 characters from X-Trace-Id, and the column was 32 — so a
        // legitimate upstream id made the audit INSERT fail after the business had already committed.
        String result = sanitizer.traceId("T".repeat(500));

        assertThat(result).hasSize(AuditInputSanitizer.TRACE_ID_MAX);
    }

    @Test
    @DisplayName("caps User-Agent, which a client can make arbitrarily long")
    void userAgent_isCappedToTheColumnWidth() {
        assertThat(sanitizer.userAgent("U".repeat(9000)))
                .hasSize(AuditInputSanitizer.USER_AGENT_MAX);
    }

    @Test
    @DisplayName("never cuts inside a surrogate pair, which would produce invalid UTF-8")
    void truncation_keepsSurrogatePairsIntact() {
        // Emoji are two chars each; cutting at an odd offset would leave a lone high surrogate, and
        // Postgres rejects that with 'invalid byte sequence for encoding "UTF8"' — trading one insert
        // failure for a different one.
        String emoji = "🚀".repeat(40);

        String result = AuditInputSanitizer.truncateOnCodePointBoundary(emoji, 31);

        assertThat(result).hasSize(30);
        assertThat(result.getBytes(StandardCharsets.UTF_8))
                .isEqualTo(result.getBytes(StandardCharsets.UTF_8));
        assertThat(Character.isHighSurrogate(result.charAt(result.length() - 1))).isFalse();
    }

    @Test
    @DisplayName("strips control characters so a header cannot forge log lines")
    void text_stripsControlCharacters() {
        assertThat(sanitizer.userAgent("curl/8.0\n2026-01-01 ERROR fake log line"))
                .doesNotContain("\n")
                .startsWith("curl/8.0");
    }

    @Test
    @DisplayName("blank values collapse to null rather than to an empty string")
    void text_blankBecomesNull() {
        assertThat(sanitizer.username("   ")).isNull();
        assertThat(sanitizer.username(null)).isNull();
    }

    @Test
    @DisplayName("an oversized snapshot is replaced by a marker, never truncated into invalid JSON")
    void jsonValue_oversized_isReplacedByAVerifiableMarker() {
        String huge = "\"" + "x".repeat(AuditInputSanitizer.JSON_VALUE_MAX_BYTES + 100) + "\"";

        String result = sanitizer.jsonValue("description", huge);

        // Cutting the string would leave a document that is not JSON at all, and the column is jsonb.
        assertThat(result).contains("\"_audit\":\"TRUNCATED\"").contains("\"sha256\"");
        assertThat(result.length()).isLessThan(huge.length());
    }

    @Test
    @DisplayName("a snapshot within budget is passed through byte-for-byte")
    void jsonValue_withinBudget_isUnchanged() {
        String json = "{\"status\":\"DRAFT\"}";

        assertThat(sanitizer.jsonValue("status", json)).isEqualTo(json);
    }

    @Test
    @DisplayName("metadata deeper than the quota is dropped whole, not partially stored")
    void metadata_overDepthQuota_isDropped() throws Exception {
        // Half a metadata document reads exactly like a complete one and would mislead whoever
        // investigates the event, so the honest answer is to store none of it.
        String deep = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":1}}}}}";

        assertThat(sanitizer.metadata(objectMapper.readTree(deep))).isNull();
    }

    @Test
    @DisplayName("a well-formed metadata object is kept")
    void metadata_withinQuota_isKept() throws Exception {
        assertThat(sanitizer.metadata(objectMapper.readTree("{\"noOp\":true}")))
                .isEqualTo("{\"noOp\":true}");
    }

    @Test
    @DisplayName("a non-object metadata document is refused")
    void metadata_mustBeAnObject() throws Exception {
        assertThat(sanitizer.metadata(objectMapper.readTree("[1,2,3]"))).isNull();
    }

    @Test
    @DisplayName("recognises sensitive field names regardless of casing or decoration")
    void sensitiveFieldNames_areRecognised() {
        assertThat(sanitizer.isSensitiveFieldName("password")).isTrue();
        assertThat(sanitizer.isSensitiveFieldName("refreshToken")).isTrue();
        assertThat(sanitizer.isSensitiveFieldName("PasswordHash")).isTrue();
        assertThat(sanitizer.isSensitiveFieldName("apiKey")).isTrue();
        assertThat(sanitizer.isSensitiveFieldName("quantity")).isFalse();
    }
}
