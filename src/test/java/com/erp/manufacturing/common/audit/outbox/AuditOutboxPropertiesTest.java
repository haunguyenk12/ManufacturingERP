package com.erp.manufacturing.common.audit.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuditOutboxProperties")
class AuditOutboxPropertiesTest {

    @Test
    @DisplayName("refuses to start with the outbox producer and the legacy listener both enabled")
    void producerAndLegacyListener_cannotBothBeEnabled() {
        // Both paths would write the same logical event, and only the outbox path assigns the
        // event_id that could deduplicate them — so a dual write silently doubles the trail. Failing
        // at startup is the only place this is cheap to notice.
        assertThatThrownBy(() -> new AuditOutboxProperties(true, true, true, null, null,
                null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy-listener-enabled");
    }

    @Test
    @DisplayName("defaults to producer and dispatcher on, legacy listener off")
    void defaults_favourTheNewPipeline() {
        AuditOutboxProperties properties = new AuditOutboxProperties(null, null, null, null, null,
                null, null, null, null, null);

        assertThat(properties.producerEnabled()).isTrue();
        assertThat(properties.dispatcherEnabled()).isTrue();
        assertThat(properties.legacyListenerEnabled()).isFalse();
    }

    @Test
    @DisplayName("backs off exponentially and stops at the ceiling")
    void backoff_doublesPerAttemptUpToTheCeiling() {
        AuditOutboxProperties properties = new AuditOutboxProperties(true, true, false, 100, 8,
                Duration.ofSeconds(2), Duration.ofSeconds(30), null, null, null);

        assertThat(properties.backoffFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.backoffFor(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.backoffFor(4)).isEqualTo(Duration.ofSeconds(16));
        // Capped: without a ceiling the delay doubles into hours and a recovered database is not
        // noticed until long after it came back.
        assertThat(properties.backoffFor(20)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("clamps nonsensical sizes instead of trusting configuration")
    void invalidSizes_areClamped() {
        AuditOutboxProperties properties = new AuditOutboxProperties(true, true, false, 0, 0,
                null, null, null, null, null);

        assertThat(properties.batchSize()).isEqualTo(100);
        assertThat(properties.maxAttempts()).isEqualTo(8);
    }
}
