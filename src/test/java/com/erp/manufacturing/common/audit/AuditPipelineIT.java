package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.audit.context.AuditContextResolver;
import com.erp.manufacturing.common.audit.context.ExplicitAuditContextProvider;
import com.erp.manufacturing.common.audit.context.HttpAuditContextProvider;
import com.erp.manufacturing.common.audit.model.AuditOutcome;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.model.AuditRequestSnapshot;
import com.erp.manufacturing.common.audit.model.AuditScope;
import com.erp.manufacturing.common.audit.model.AuditSource;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxEntry;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxProcessor;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxProperties;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxRepository;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxStatus;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxWriter;
import com.erp.manufacturing.common.audit.outbox.AuditStandaloneOutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The audit pipeline against a real Postgres: outbox atomicity, failure survival, idempotency, input
 * hardening, lease recovery and database-enforced immutability (AuditRefactorPlan AR-0, AR-2, AR-7).
 *
 * <p><strong>Why these need a real database.</strong> Every property under test here is invisible to a
 * mocked repository, which is the lesson rule {@code R7} exists for:
 *
 * <ul>
 *   <li>"the outbox row commits with the business transaction" is a statement about commit and
 *       rollback — a mock has neither;</li>
 *   <li>"a redelivery cannot duplicate" is enforced by a unique index;</li>
 *   <li>"the application cannot rewrite the trail" is enforced by a trigger;</li>
 *   <li>"a long header cannot destroy the event" is about column widths.</li>
 * </ul>
 *
 * <p>{@code @Transactional(NOT_SUPPORTED)} switches off the transaction {@code @DataJpaTest} normally
 * wraps each test in. That wrapper rolls back at the end, which would make every assertion here
 * meaningless: the whole point is to observe what survives a real commit and what does not survive a
 * real rollback. Transactions are therefore opened explicitly, per scenario, with
 * {@link TransactionTemplate}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuditRecorder.class, AuditOutboxWriter.class, AuditStandaloneOutboxWriter.class,
        AuditPayloadCodec.class, AuditLogMaterializer.class, AuditOutboxProcessor.class,
        AuditInputSanitizer.class, AuditMetrics.class, AuditContextResolver.class,
        HttpAuditContextProvider.class, ExplicitAuditContextProvider.class,
        AuditPipelineIT.PipelineConfig.class})
class AuditPipelineIT extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    static class PipelineConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
        @Bean Clock clock() { return Clock.systemUTC(); }

        /**
         * Short lease and low attempt budget so the recovery and dead-letter paths are reachable in a
         * test without sleeping for minutes.
         */
        @Bean AuditOutboxProperties auditOutboxProperties() {
            return new AuditOutboxProperties(true, true, false, 100, 2,
                    Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(1),
                    Duration.ofSeconds(60), Duration.ofDays(3));
        }
    }

    @Autowired AuditRecorder auditRecorder;
    @Autowired AuditOutboxRepository outboxRepository;
    @Autowired AuditOutboxProcessor processor;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired AuditLogChangeRepository auditLogChangeRepository;
    @Autowired AuditLogEntityRowRepository auditLogEntityRowRepository;
    @Autowired AuditLogMaterializer materializer;
    @Autowired AuditPayloadCodec codec;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void reset() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                        "TRUNCATE audit_outbox, audit_log_changes, audit_log_entities, audit_logs CASCADE")
                .executeUpdate());
    }

    // ── AR-2: atomicity with the business transaction ────────────────────

    @Test
    void successEvent_committedWithTheBusinessTransaction_thenMaterialisedExactlyOnce() {
        UUID roleId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                auditRecorder.recordSuccess(draft(AuditAction.PERMISSION_GRANTED)
                        .primaryEntity("Role", roleId, "ADMIN")
                        .relatedEntity("Permission", UUID.randomUUID(), "PERM_AUDIT_READ")
                        .scope(AuditScope.ofCompany(UUID.randomUUID()))
                        .build()));

        assertThat(outboxRepository.countByStatus(AuditOutboxStatus.PENDING)).isEqualTo(1);

        processor.claimBatch("test-worker").forEach(processor::deliver);

        List<AuditLog> rows = auditLogRepository.findByActionOrderByCreatedAtDesc(
                AuditAction.PERMISSION_GRANTED.name());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEntityId()).isEqualTo(roleId.toString());
        assertThat(rows.get(0).getStatus()).isEqualTo(AuditOutcome.SUCCESS.name());
        assertThat(rows.get(0).getSource()).isEqualTo(AuditSource.HTTP.name());
        // The related permission is the fact the single-target columns could never carry.
        assertThat(auditLogEntityRowRepository
                .findByAuditIdOrderByRelationAscEntityTypeAsc(rows.get(0).getAuditId()))
                .extracting(AuditLogEntityRow::getEntityType)
                .containsExactlyInAnyOrder("Role", "Permission");
        assertThat(outboxRepository.countByStatus(AuditOutboxStatus.PROCESSED)).isEqualTo(1);
    }

    @Test
    void businessRollback_discardsTheSuccessEventButKeepsTheFailureEvent() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            auditRecorder.recordSuccess(draft(AuditAction.WORK_ORDER_CREATED)
                    .primaryEntity("WorkOrder", UUID.randomUUID(), "WO-1").build());
            auditRecorder.recordFailure(draft(AuditAction.WORK_ORDER_CREATED)
                    .outcome(AuditOutcome.FAILURE)
                    .reasonCode("INSUFFICIENT_AVAILABLE_STOCK")
                    .primaryEntity("WorkOrder", UUID.randomUUID(), "WO-1").build());
            throw new IllegalStateException("business rule rejected the command");
        })).isInstanceOf(IllegalStateException.class);

        processor.claimBatch("test-worker").forEach(processor::deliver);

        List<AuditLog> rows = auditLogRepository.findByActionOrderByCreatedAtDesc(
                AuditAction.WORK_ORDER_CREATED.name());
        // The success event rolled back with the command it described; the failure event, written in
        // its own REQUIRES_NEW transaction, survived it. Under the old AFTER_COMMIT listener neither
        // existed: the rollback took the failure record with it.
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(AuditOutcome.FAILURE.name());
        assertThat(rows.get(0).getReasonCode()).isEqualTo("INSUFFICIENT_AVAILABLE_STOCK");
    }

    @Test
    void authEvent_withNoAmbientTransaction_isStillRecorded() {
        // AuthService carries no @Transactional at all, which is why every login, failed login,
        // lockout and token-reuse event was silently dropped by the AFTER_COMMIT listener.
        auditRecorder.recordStandalone(draft(AuditAction.LOGIN_FAILED)
                .source(AuditSource.AUTH)
                .outcome(AuditOutcome.FAILURE)
                .reasonCode("INVALID_CREDENTIALS")
                .primaryEntity("User", null, "attacker")
                .build());

        processor.claimBatch("test-worker").forEach(processor::deliver);

        assertThat(auditLogRepository.findByActionOrderByCreatedAtDesc(AuditAction.LOGIN_FAILED.name()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getUsername()).isNull();
                    assertThat(row.getEntityName()).isEqualTo("attacker");
                    assertThat(row.getSource()).isEqualTo(AuditSource.AUTH.name());
                });
    }

    // ── AR-1: client-controlled input cannot destroy the event ───────────

    @Test
    void oversizedTraceIdAndUserAgent_areCappedInsteadOfFailingTheInsert() {
        String longTraceId = "T".repeat(300);
        String longUserAgent = "U".repeat(5000);

        auditRecorder.recordStandalone(AuditRecordDraft.builder()
                .action(AuditAction.LOGIN)
                .source(AuditSource.AUTH)
                .request(new AuditRequestSnapshot(longTraceId, "10.0.0.1", longUserAgent,
                        "POST", "/api/auth/v1/login"))
                .primaryEntity("User", UUID.randomUUID(), "operator")
                .build());

        processor.claimBatch("test-worker").forEach(processor::deliver);

        // Before AR-1 this INSERT failed after the business transaction had already committed: the
        // change survived, the record of it did not, and any client could suppress its own audit
        // trail with one long header.
        assertThat(auditLogRepository.findByActionOrderByCreatedAtDesc(AuditAction.LOGIN.name()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getTraceId()).hasSize(AuditInputSanitizer.TRACE_ID_MAX);
                    assertThat(row.getUserAgent()).hasSize(AuditInputSanitizer.USER_AGENT_MAX);
                });
    }

    @Test
    void oversizedFieldSnapshot_isReplacedByAMarkerAndTheEventSurvives() {
        String hugeJson = "\"" + "x".repeat(AuditInputSanitizer.JSON_VALUE_MAX_BYTES + 10) + "\"";

        auditRecorder.recordStandalone(draft(AuditAction.ITEM_UPDATED)
                .primaryEntity("Item", UUID.randomUUID(), "ITEM-1")
                .changes(List.of(new AuditFieldChange("description", null, hugeJson,
                        AuditLogChangeType.UPDATE)))
                .build());

        processor.claimBatch("test-worker").forEach(processor::deliver);

        AuditLog parent = auditLogRepository.findByActionOrderByCreatedAtDesc(
                AuditAction.ITEM_UPDATED.name()).get(0);
        assertThat(auditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc(parent.getAuditId()))
                .singleElement()
                .satisfies(change -> {
                    // Replaced, never truncated: half a JSON document is not JSON, and the column is
                    // jsonb. The marker keeps the fact that the field changed plus a verifiable hash.
                    assertThat(change.getNewValue()).contains("TRUNCATED").contains("sha256");
                });
    }

    // ── AR-2: idempotency and recovery ───────────────────────────────────

    @Test
    void redeliveryOfTheSameEventId_producesNoDuplicateRow() {
        AuditRecordDraft event = draft(AuditAction.USER_CREATED)
                .primaryEntity("User", UUID.randomUUID(), "operator").build();

        transactionTemplate.executeWithoutResult(status ->
                materializer.materialize(event, codec.hash(codec.toNode(event))));
        transactionTemplate.executeWithoutResult(status ->
                materializer.materialize(event, codec.hash(codec.toNode(event))));

        assertThat(auditLogRepository.findByActionOrderByCreatedAtDesc(AuditAction.USER_CREATED.name()))
                .hasSize(1);
    }

    @Test
    void leaseExpiry_returnsAStrandedRowToTheQueueInsteadOfLosingIt() {
        auditRecorder.recordStandalone(draft(AuditAction.LOGOUT)
                .primaryEntity("User", UUID.randomUUID(), "operator").build());

        // Claim without delivering: exactly what a worker that crashed mid-flight leaves behind.
        List<UUID> claimed = processor.claimBatch("worker-that-died");
        assertThat(claimed).hasSize(1);
        assertThat(outboxRepository.countByStatus(AuditOutboxStatus.PROCESSING)).isEqualTo(1);

        assertThat(processor.reclaimExpiredLeases()).isEqualTo(1);
        assertThat(outboxRepository.countByStatus(AuditOutboxStatus.PENDING)).isEqualTo(1);

        processor.claimBatch("healthy-worker").forEach(processor::deliver);
        assertThat(auditLogRepository.findByActionOrderByCreatedAtDesc(AuditAction.LOGOUT.name()))
                .hasSize(1);
    }

    @Test
    void unreadablePayload_isRetriedThenDeadLetteredAndNeverDeleted() {
        UUID eventId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> outboxRepository.save(
                AuditOutboxEntry.builder()
                        .eventId(eventId)
                        .payload("{\"v\":1,\"action\":\"NOT_A_REAL_ACTION\"}")
                        .occurredAt(Instant.now())
                        .nextAttemptAt(Instant.now().minusSeconds(1))
                        .build()));

        // maxAttempts is 2 in this slice, so two passes exhaust the budget.
        for (int attempt = 0; attempt < 3; attempt++) {
            processor.claimBatch("test-worker").forEach(processor::deliver);
        }

        assertThat(outboxRepository.countByStatus(AuditOutboxStatus.FAILED)).isEqualTo(1);
        // A dead letter is evidence that something was NOT recorded; deleting it would destroy the
        // only signal that the trail has a hole.
        assertThat(outboxRepository.findByEventId(eventId)).isPresent();
    }

    // ── AR-7: the database refuses to let the application rewrite history ─

    @Test
    void auditRowsCannotBeUpdatedOrDeletedByTheApplicationCredential() {
        auditRecorder.recordStandalone(draft(AuditAction.LOGIN)
                .primaryEntity("User", UUID.randomUUID(), "operator").build());
        processor.claimBatch("test-worker").forEach(processor::deliver);
        UUID auditId = auditLogRepository.findByActionOrderByCreatedAtDesc(
                AuditAction.LOGIN.name()).get(0).getAuditId();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                entityManager.createNativeQuery(
                                "UPDATE audit_logs SET username = 'someone else' WHERE audit_id = :id")
                        .setParameter("id", auditId)
                        .executeUpdate()))
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                entityManager.createNativeQuery("DELETE FROM audit_logs WHERE audit_id = :id")
                        .setParameter("id", auditId)
                        .executeUpdate()))
                .hasMessageContaining("append-only");

        assertThat(auditLogRepository.findById(auditId)).isPresent();
    }

    @Test
    void maintenancePath_isTheOnlyWayToRemoveAnAuditRow() {
        auditRecorder.recordStandalone(draft(AuditAction.LOGOUT_ALL)
                .primaryEntity("User", UUID.randomUUID(), "operator").build());
        processor.claimBatch("test-worker").forEach(processor::deliver);
        UUID auditId = auditLogRepository.findByActionOrderByCreatedAtDesc(
                AuditAction.LOGOUT_ALL.name()).get(0).getAuditId();

        transactionTemplate.executeWithoutResult(status -> {
            // Exactly what AuditRetentionService does, and the only escape hatch that exists.
            entityManager.createNativeQuery("SET LOCAL audit.maintenance = 'on'").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM audit_logs WHERE audit_id = :id")
                    .setParameter("id", auditId)
                    .executeUpdate();
        });

        assertThat(auditLogRepository.findById(auditId)).isEmpty();
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private AuditRecordDraft.Builder draft(AuditAction action) {
        return AuditRecordDraft.builder()
                .action(action)
                .source(AuditSource.HTTP)
                .request(new AuditRequestSnapshot("trace-" + UUID.randomUUID(), "127.0.0.1",
                        "JUnit", "POST", "/api/v1/test"));
    }
}
