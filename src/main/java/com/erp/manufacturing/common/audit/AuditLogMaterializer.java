package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.model.AuditEntityRef;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Turns one decoded outbox payload into the immutable audit rows: the parent {@code audit_logs} row,
 * its {@code audit_log_entities} targets and its {@code audit_log_changes} field diffs (AR-2).
 *
 * <p><strong>Idempotency.</strong> The producer minted {@code eventId} and it travels with the
 * payload, so a redelivery after a crash carries the same identity. This checks for an existing row
 * before writing, and the unique index on {@code audit_logs.event_id} backs that check up for the
 * case two workers race past it — the check keeps the normal path cheap, the index keeps the
 * guarantee real. A duplicate is treated as success: the event <em>is</em> recorded, which is all the
 * dispatcher needs to know.
 *
 * <p><strong>Atomicity.</strong> All three inserts share one transaction, so an event can never
 * appear with half its detail — a parent row whose changes silently went missing reads like a
 * command that changed nothing.
 *
 * <p>The primary target is written to both the legacy {@code audit_logs.entity_*} columns and the new
 * {@code audit_log_entities} table for the whole compatibility window. Dual-writing is what lets the
 * read API, the FE and every existing query keep working while the richer model fills in underneath.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogMaterializer {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogChangeRepository auditLogChangeRepository;
    private final AuditLogEntityRowRepository auditLogEntityRowRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void materialize(AuditRecordDraft draft, String payloadHash) {
        if (auditLogRepository.existsByEventId(draft.eventId())) {
            log.debug("[Audit] Event {} already materialised; redelivery ignored", draft.eventId());
            return;
        }

        AuditEntityRef primary = draft.primaryEntity();
        AuditLog parent = auditLogRepository.saveAndFlush(AuditLog.builder()
                .eventId(draft.eventId())
                .userId(draft.actor().userId())
                .username(draft.actor().username())
                .action(draft.action().name())
                .entityType(primary == null ? null : primary.entityType())
                .entityId(primary == null ? null : primary.entityId())
                .entityName(primary == null ? null : primary.entityName())
                .description(draft.description())
                .status(draft.outcome().name())
                .reasonCode(draft.reasonCode())
                .source(draft.source().name())
                .clientIp(draft.request().clientIp())
                .userAgent(draft.request().userAgent())
                .traceId(draft.request().traceId())
                .httpMethod(draft.request().httpMethod())
                .requestPath(draft.request().requestPath())
                .companyId(draft.scope().companyId())
                .plantId(draft.scope().plantId())
                .warehouseId(draft.scope().warehouseId())
                .metadata(draft.metadataJson())
                .payloadHash(payloadHash)
                .occurredAt(draft.occurredAt())
                .build());

        if (!draft.entities().isEmpty()) {
            List<AuditLogEntityRow> rows = draft.entities().stream()
                    .map(ref -> AuditLogEntityRow.builder()
                            .auditId(parent.getAuditId())
                            .relation(ref.relation())
                            .entityType(ref.entityType())
                            .entityId(ref.entityId())
                            .entityName(ref.entityName())
                            .build())
                    // entity_type is NOT NULL: a ref that only carries a name has nothing to index on
                    // and would fail the insert, taking the whole event down with it.
                    .filter(row -> row.getEntityType() != null)
                    .toList();
            auditLogEntityRowRepository.saveAll(rows);
        }

        if (!draft.changes().isEmpty()) {
            auditLogChangeRepository.saveAll(draft.changes().stream()
                    .map(change -> AuditLogChange.builder()
                            .auditId(parent.getAuditId())
                            .fieldName(change.fieldName())
                            .oldValue(change.oldValue())
                            .newValue(change.newValue())
                            .changeType(change.changeType())
                            .build())
                    .toList());
        }
    }
}
