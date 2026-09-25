package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.context.AuditContext;
import com.erp.manufacturing.common.audit.context.AuditContextResolver;
import com.erp.manufacturing.common.audit.model.AuditActorSnapshot;
import com.erp.manufacturing.common.audit.model.AuditOutcome;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.model.AuditRequestSnapshot;
import com.erp.manufacturing.common.audit.model.AuditScope;
import com.erp.manufacturing.common.audit.model.AuditSource;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxProperties;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxWriter;
import com.erp.manufacturing.common.audit.outbox.AuditStandaloneOutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AuditRecorder")
class AuditRecorderTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    private final AuditContextResolver contextResolver = mock(AuditContextResolver.class);
    private final AuditOutboxWriter outboxWriter = mock(AuditOutboxWriter.class);
    private final AuditStandaloneOutboxWriter standaloneWriter = mock(AuditStandaloneOutboxWriter.class);
    private final AuditMetrics metrics = new AuditMetrics(new SimpleMeterRegistry());
    private final AuditInputSanitizer sanitizer = new AuditInputSanitizer(new ObjectMapper(), metrics);
    private final AuditOutboxProperties properties = new AuditOutboxProperties(
            true, true, false, null, null, null, null, null, null, null);

    private final AuditRecorder recorder = new AuditRecorder(contextResolver, outboxWriter,
            standaloneWriter, sanitizer, properties, metrics, Clock.fixed(NOW, ZoneOffset.UTC));

    private AuditRecordDraft.Builder draft() {
        when(contextResolver.resolve()).thenReturn(AuditContext.system("test"));
        return recorder.draft(AuditAction.LOGIN);
    }

    @Test
    @DisplayName("a success joins the caller's transaction; a failure gets its own")
    void successAndFailure_useDifferentWriters() {
        // This is the whole point of the split. A SUCCESS must vanish if the command rolls back; a
        // FAILURE must survive precisely because the command rolled back.
        recorder.recordSuccess(draft().build());
        verify(outboxWriter).append(any());
        verifyNoInteractions(standaloneWriter);

        recorder.recordFailure(draft().build());
        verify(standaloneWriter).append(any());
        verify(outboxWriter).append(any()); // still only the one from the success above
    }

    @Test
    @DisplayName("stamps the event from the injected clock so tests can pin it")
    void draft_takesItsTimestampFromTheClock() {
        assertThat(draft().build().occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("caps every client-controlled value before it can reach a column")
    void sanitisation_isAppliedToTheWholeDraft() {
        // Applied here, on the whole draft, rather than at each producer — so a new producer cannot
        // forget it.
        recorder.recordStandalone(draft()
                .request(new AuditRequestSnapshot("T".repeat(400), "1.2.3.4", "U".repeat(4000),
                        "POST", "/x"))
                .actor(AuditActorSnapshot.user(UUID.randomUUID(), "n".repeat(300)))
                .primaryEntity("WorkOrder", "w".repeat(400), "N".repeat(400))
                .build());

        ArgumentCaptor<AuditRecordDraft> captor = ArgumentCaptor.forClass(AuditRecordDraft.class);
        verify(standaloneWriter).append(captor.capture());
        AuditRecordDraft sanitized = captor.getValue();
        assertThat(sanitized.request().traceId()).hasSize(AuditInputSanitizer.TRACE_ID_MAX);
        assertThat(sanitized.request().userAgent()).hasSize(AuditInputSanitizer.USER_AGENT_MAX);
        assertThat(sanitized.actor().username()).hasSize(AuditInputSanitizer.USERNAME_MAX);
        assertThat(sanitized.primaryEntity().entityId()).hasSize(AuditInputSanitizer.ENTITY_ID_MAX);
        assertThat(sanitized.primaryEntity().entityName()).hasSize(AuditInputSanitizer.ENTITY_NAME_MAX);
    }

    @Test
    @DisplayName("drops a sensitive field rather than storing it")
    void sensitiveChanges_areDropped() {
        recorder.recordSuccess(draft()
                .changes(List.of(
                        new AuditFieldChange("password", "\"old\"", "\"new\"", AuditLogChangeType.UPDATE),
                        new AuditFieldChange("status", "\"DRAFT\"", "\"ACTIVE\"", AuditLogChangeType.UPDATE)))
                .build());

        ArgumentCaptor<AuditRecordDraft> captor = ArgumentCaptor.forClass(AuditRecordDraft.class);
        verify(outboxWriter).append(captor.capture());
        assertThat(captor.getValue().changes())
                .extracting(AuditFieldChange::fieldName)
                .containsExactly("status");
    }

    @Test
    @DisplayName("FAIL_OPEN: a writer failure is absorbed, never rethrown at the caller")
    void writerFailure_isSwallowed() {
        // Decision §11.1, system-wide. An audit-side defect must not turn a successful command into
        // an error the user sees.
        doThrow(new IllegalStateException("outbox unavailable")).when(outboxWriter).append(any());

        assertThatCode(() -> recorder.recordSuccess(draft().build())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("records nothing at all when the producer is switched off")
    void producerDisabled_writesNothing() {
        AuditOutboxProperties disabled = new AuditOutboxProperties(false, true, false,
                null, null, null, null, null, null, null);
        AuditRecorder stopped = new AuditRecorder(contextResolver, outboxWriter, standaloneWriter,
                sanitizer, disabled, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
        when(contextResolver.resolve()).thenReturn(AuditContext.system("test"));

        stopped.recordSuccess(stopped.draft(AuditAction.LOGIN).build());

        verify(outboxWriter, never()).append(any());
        verify(standaloneWriter, never()).append(any());
    }

    @Test
    @DisplayName("forces the outcome onto the draft so a caller cannot mislabel a failure as success")
    void outcome_isSetByTheRecorderNotByTheCaller() {
        recorder.recordFailure(draft().outcome(AuditOutcome.SUCCESS).build());

        ArgumentCaptor<AuditRecordDraft> captor = ArgumentCaptor.forClass(AuditRecordDraft.class);
        verify(standaloneWriter).append(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(AuditOutcome.FAILURE);
    }

    @Test
    @DisplayName("inherits actor, source and scope from the ambient context")
    void draft_inheritsTheAmbientContext() {
        UUID actorId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(contextResolver.resolve()).thenReturn(new AuditContext(
                AuditActorSnapshot.user(actorId, "operator"),
                new AuditRequestSnapshot("trace-9", "10.0.0.9", "curl", "GET", "/v1/x"),
                AuditScope.ofPlant(plantId), AuditSource.SCHEDULED_JOB));

        AuditRecordDraft built = recorder.draft(AuditAction.MRP_RUN).build();

        assertThat(built.actor().userId()).isEqualTo(actorId);
        assertThat(built.source()).isEqualTo(AuditSource.SCHEDULED_JOB);
        assertThat(built.scope().plantId()).isEqualTo(plantId);
        assertThat(built.request().traceId()).isEqualTo("trace-9");
    }
}
