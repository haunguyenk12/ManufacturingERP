package com.erp.manufacturing.common.audit.model;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditFieldChange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The complete description of one audit event, built on the calling thread and then handed to
 * {@code AuditRecorder} (AR-3).
 *
 * <p>It answers, in one object, the seven questions the plan requires an audit trail to answer: who
 * ({@link #actor}), what ({@link #action}), on which object ({@link #entities}), in which scope
 * ({@link #scope}), when ({@link #occurredAt}), with what result ({@link #outcome} plus
 * {@link #reasonCode}) and what changed ({@link #changes}).
 *
 * <p>{@link #eventId} is assigned here, on the producer side, and is the identity that makes the
 * whole pipeline idempotent: the outbox row carries it, the dispatcher writes it onto the audit row,
 * and a unique index on that column is what turns "retry after a crash" into "no duplicate". It must
 * therefore be minted once, when the event is described, and never regenerated on a retry.
 */
public record AuditRecordDraft(
        UUID eventId,
        Instant occurredAt,
        AuditAction action,
        AuditOutcome outcome,
        AuditActorSnapshot actor,
        List<AuditEntityRef> entities,
        AuditScope scope,
        AuditSource source,
        AuditRequestSnapshot request,
        String reasonCode,
        String description,
        List<AuditFieldChange> changes,
        String metadataJson
) {

    public AuditRecordDraft {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(source, "source");
        entities = entities == null ? List.of() : List.copyOf(entities);
        changes = changes == null ? List.of() : List.copyOf(changes);
        actor = actor == null ? new AuditActorSnapshot(null, null, AuditActorSnapshot.AuditActorType.USER) : actor;
        scope = scope == null ? AuditScope.EMPTY : scope;
        request = request == null ? AuditRequestSnapshot.EMPTY : request;
    }

    /** The object the action is named after, if the command managed to identify one. */
    public AuditEntityRef primaryEntity() {
        return entities.stream().filter(AuditEntityRef::isPrimary).findFirst().orElse(null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder, not a long constructor: the record has thirteen components and most callers
     * set four. A positional constructor at that width is how arguments silently swap places.
     */
    public static final class Builder {

        private UUID eventId = UUID.randomUUID();
        private Instant occurredAt = Instant.now();
        private AuditAction action;
        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private AuditActorSnapshot actor;
        private final List<AuditEntityRef> entities = new ArrayList<>();
        private AuditScope scope = AuditScope.EMPTY;
        private AuditSource source = AuditSource.HTTP;
        private AuditRequestSnapshot request = AuditRequestSnapshot.EMPTY;
        private String reasonCode;
        private String description;
        private List<AuditFieldChange> changes = List.of();
        private String metadataJson;

        public Builder eventId(UUID eventId) { this.eventId = eventId; return this; }
        public Builder occurredAt(Instant occurredAt) { this.occurredAt = occurredAt; return this; }
        public Builder action(AuditAction action) { this.action = action; return this; }
        public Builder outcome(AuditOutcome outcome) { this.outcome = outcome; return this; }
        public Builder actor(AuditActorSnapshot actor) { this.actor = actor; return this; }
        public Builder scope(AuditScope scope) { this.scope = scope; return this; }
        public Builder source(AuditSource source) { this.source = source; return this; }
        public Builder request(AuditRequestSnapshot request) { this.request = request; return this; }
        public Builder reasonCode(String reasonCode) { this.reasonCode = reasonCode; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder changes(List<AuditFieldChange> changes) { this.changes = changes; return this; }
        public Builder metadataJson(String metadataJson) { this.metadataJson = metadataJson; return this; }

        /** Replaces any primary already set, so a descriptor can override the annotation default. */
        public Builder primaryEntity(AuditEntityRef ref) {
            if (ref == null || ref.isEmpty()) {
                return this;
            }
            entities.removeIf(AuditEntityRef::isPrimary);
            entities.add(0, ref);
            return this;
        }

        public Builder primaryEntity(String entityType, Object entityId, String entityName) {
            return primaryEntity(AuditEntityRef.primary(entityType, entityId, entityName));
        }

        public Builder relatedEntity(AuditEntityRef ref) {
            if (ref != null && !ref.isEmpty()) {
                entities.add(ref);
            }
            return this;
        }

        public Builder relatedEntity(String entityType, Object entityId, String entityName) {
            return relatedEntity(AuditEntityRef.related(entityType, entityId, entityName));
        }

        public AuditRecordDraft build() {
            return new AuditRecordDraft(eventId, occurredAt, action, outcome, actor, entities,
                    scope, source, request, reasonCode, description, changes, metadataJson);
        }
    }
}
