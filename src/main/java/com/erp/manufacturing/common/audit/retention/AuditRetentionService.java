package com.erp.manufacturing.common.audit.retention;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditRecorder;
import com.erp.manufacturing.common.audit.context.AuditContext;
import com.erp.manufacturing.common.audit.context.ExplicitAuditContextProvider;
import com.erp.manufacturing.common.audit.model.AuditActorSnapshot;
import com.erp.manufacturing.common.audit.model.AuditRequestSnapshot;
import com.erp.manufacturing.common.audit.model.AuditScope;
import com.erp.manufacturing.common.audit.model.AuditSource;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Removes audit rows that have aged past the retention window (AR-8).
 *
 * <p><strong>Why this is the only code allowed to delete audit rows.</strong> {@code V68} installs a
 * trigger that rejects UPDATE and DELETE on every audit table, for every role including superusers.
 * The single escape hatch is a transaction-scoped GUC, and this service is the only place that sets
 * it. That inverts the usual arrangement: deletion is impossible by default and possible only along
 * one path that is named, configured, rate-limited and — see below — audited itself.
 *
 * <p><strong>Batched.</strong> Deleting years of trail in one statement takes a long lock on the
 * table every audit write also needs, so a purge would stall the pipeline it is maintaining. Each
 * batch is its own transaction, which also means the GUC is set and dropped per batch rather than
 * left on for the duration.
 *
 * <p><strong>Self-auditing.</strong> The purge records its own event, under
 * {@link AuditSource#SCHEDULED_JOB} with no user, before it deletes anything. An operation that can
 * erase evidence has to leave some.
 *
 * <p><strong>On partitioning.</strong> The plan allows monthly partitioning "if production volume
 * proves it necessary". No such measurement exists yet, and converting a populated table to a
 * partitioned one is a rewrite with a maintenance window. Batched deletion is the honest first step;
 * partitioning is the answer once the volume numbers exist to justify it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRetentionService {

    private final EntityManager entityManager;
    private final AuditRetentionProperties properties;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    /**
     * Deletes one batch of expired rows.
     *
     * @return how many parent rows were removed; {@code 0} when retention is disabled or nothing aged out
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeOneBatch() {
        if (!properties.enabled()) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(properties.retentionMonths() * 30L, ChronoUnit.DAYS);

        // SET LOCAL: scoped to this transaction, so the append-only guard is back in force the moment
        // the batch commits — including if it rolls back.
        entityManager.createNativeQuery("SET LOCAL audit.maintenance = 'on'").executeUpdate();

        // Children go first: the FK is ON DELETE CASCADE, but a cascade fires the child trigger too,
        // and relying on cascade would hide how much is actually being removed.
        int deletedChanges = entityManager.createNativeQuery("""
                        DELETE FROM audit_log_changes
                        WHERE audit_id IN (
                            SELECT audit_id FROM audit_logs
                            WHERE COALESCE(occurred_at, created_at) < :cutoff
                            LIMIT :batchSize)
                        """)
                .setParameter("cutoff", cutoff)
                .setParameter("batchSize", properties.batchSize())
                .executeUpdate();

        int deletedEntities = entityManager.createNativeQuery("""
                        DELETE FROM audit_log_entities
                        WHERE audit_id IN (
                            SELECT audit_id FROM audit_logs
                            WHERE COALESCE(occurred_at, created_at) < :cutoff
                            LIMIT :batchSize)
                        """)
                .setParameter("cutoff", cutoff)
                .setParameter("batchSize", properties.batchSize())
                .executeUpdate();

        int deletedLogs = entityManager.createNativeQuery("""
                        DELETE FROM audit_logs
                        WHERE audit_id IN (
                            SELECT audit_id FROM audit_logs
                            WHERE COALESCE(occurred_at, created_at) < :cutoff
                            LIMIT :batchSize)
                        """)
                .setParameter("cutoff", cutoff)
                .setParameter("batchSize", properties.batchSize())
                .executeUpdate();

        if (deletedLogs > 0) {
            log.info("[Audit] Retention purge removed {} events ({} changes, {} entity targets) older than {}",
                    deletedLogs, deletedChanges, deletedEntities, cutoff);
            recordPurge(cutoff, deletedLogs);
        }
        return deletedLogs;
    }

    /**
     * Records the purge as an ordinary audit event, on the maintenance path rather than a request.
     *
     * <p>{@code ExplicitAuditContextProvider} is what makes that possible: there is no servlet request
     * here, and the old pipeline could only describe events that had one.
     */
    private void recordPurge(Instant cutoff, int deletedCount) {
        AuditContext context = new AuditContext(
                AuditActorSnapshot.system("AuditRetentionService"),
                AuditRequestSnapshot.EMPTY, AuditScope.EMPTY, AuditSource.SCHEDULED_JOB);
        ExplicitAuditContextProvider.runWith(context, () -> auditRecorder.recordStandalone(
                auditRecorder.draft(AuditAction.CONFIG_CHANGED)
                        .source(AuditSource.SCHEDULED_JOB)
                        .reasonCode("AUDIT_RETENTION_PURGE")
                        .description("Purged " + deletedCount + " audit events recorded before " + cutoff)
                        .metadataJson("{\"deletedCount\":" + deletedCount
                                + ",\"cutoff\":\"" + cutoff + "\"}")
                        .build()));
    }
}
