package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Filters for {@code GET /v1/audit-logs} (AR-6).
 *
 * <p>Grouped into one object rather than passed as fourteen positional parameters. At that width a
 * method signature is a hazard: {@code entityType} and {@code relatedEntityType} are both
 * {@code String}, {@code companyId} / {@code plantId} / {@code warehouseId} are all {@code UUID}, and
 * a swapped pair compiles cleanly and silently returns the wrong page.
 *
 * @param relatedEntityType answers "every event that touched this object", including events named
 *                          after something else — the question the single-target columns cannot
 *                          answer at all
 */
public record AuditLogSearchCriteria(
        UUID actorUserId,
        String entityType,
        String entityId,
        String action,
        String outcome,
        String source,
        UUID companyId,
        UUID plantId,
        UUID warehouseId,
        String traceId,
        Instant from,
        Instant to,
        String relatedEntityType,
        String relatedEntityId
) {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Rejects a reversed time window.
     *
     * <p>Without this the query is perfectly valid and returns an empty page, so a client that swaps
     * {@code from} and {@code to} is told "there is no audit trail for this period" — the single most
     * misleading answer this endpoint can give.
     */
    public void validate() {
        if (from != null && to != null && from.isAfter(to)) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Audit log filter 'from' must not be after 'to'");
        }
    }

    public static final class Builder {
        private UUID actorUserId;
        private String entityType;
        private String entityId;
        private String action;
        private String outcome;
        private String source;
        private UUID companyId;
        private UUID plantId;
        private UUID warehouseId;
        private String traceId;
        private Instant from;
        private Instant to;
        private String relatedEntityType;
        private String relatedEntityId;

        public Builder actorUserId(UUID v) { this.actorUserId = v; return this; }
        public Builder entityType(String v) { this.entityType = v; return this; }
        public Builder entityId(String v) { this.entityId = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder outcome(String v) { this.outcome = v; return this; }
        public Builder source(String v) { this.source = v; return this; }
        public Builder companyId(UUID v) { this.companyId = v; return this; }
        public Builder plantId(UUID v) { this.plantId = v; return this; }
        public Builder warehouseId(UUID v) { this.warehouseId = v; return this; }
        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder from(Instant v) { this.from = v; return this; }
        public Builder to(Instant v) { this.to = v; return this; }
        public Builder relatedEntityType(String v) { this.relatedEntityType = v; return this; }
        public Builder relatedEntityId(String v) { this.relatedEntityId = v; return this; }

        public AuditLogSearchCriteria build() {
            return new AuditLogSearchCriteria(actorUserId, entityType, entityId, action, outcome,
                    source, companyId, plantId, warehouseId, traceId, from, to,
                    relatedEntityType, relatedEntityId);
        }
    }
}
