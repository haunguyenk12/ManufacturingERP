package com.erp.manufacturing.common.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log entry – append-only, never updated or soft-deleted.
 * <p>
 * Does NOT extend {@link BaseEntity} because:
 * <ul>
 *   <li>It has no {@code updated_at} / {@code updated_by} (immutable)</li>
 *   <li>It has no version (no optimistic locking needed)</li>
 *   <li>It has own {@code created_at} set at DB level</li>
 * </ul>
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_id",    columnList = "user_id"),
        @Index(name = "idx_audit_action",     columnList = "action"),
        @Index(name = "idx_audit_entity",     columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_audit_trace_id",   columnList = "trace_id"),
        @Index(name = "idx_audit_user_time",  columnList = "user_id, created_at DESC"),
        @Index(name = "idx_audit_plant_id",   columnList = "plant_id"),
        @Index(name = "idx_audit_occurred_at", columnList = "occurred_at DESC, audit_id"),
        @Index(name = "idx_audit_action_time", columnList = "action, occurred_at DESC"),
        @Index(name = "idx_audit_plant_time",  columnList = "plant_id, occurred_at DESC"),
        @Index(name = "idx_audit_company_id",  columnList = "company_id")
})
@Immutable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id", updatable = false, nullable = false)
    private UUID auditId;

    /** Null for unauthenticated events (e.g. failed login). */
    @Column(name = "user_id")
    private UUID userId;

    /** Snapshot of username at time of action – user may change username in future. */
    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    /** Java class name of the affected entity (e.g. "WorkOrder"). Null for auth events. */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** Primary key of the affected entity as string. */
    @Column(name = "entity_id", length = 255)
    private String entityId;

    /** Human-readable name/code/number snapshot of the affected entity. */
    @Column(name = "entity_name", length = 255)
    private String entityName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "SUCCESS";

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Widened to 64 in {@code V67} to match what {@code TraceIdFilter} already accepts. */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    /**
     * Added by {@code V54} and {@code null} on every row until the audit refactor (AR-3/AR-5), because
     * nothing populated it — filtering by it was a correct no-op rather than a bug. Rows written by
     * the outbox pipeline carry the scope the command itself resolved. Historical rows stay
     * {@code null}: inferring a past row's plant from today's data would falsify the snapshot.
     */
    @Column(name = "plant_id")
    private UUID plantId;

    // ── V67 (audit refactor) — all nullable, never backfilled ────────────

    /**
     * Logical identity assigned by the producer and carried through {@code audit_outbox}. A unique
     * index on this column is what makes redelivery after a crash idempotent instead of duplicating.
     * {@code null} on every row written before {@code V67}.
     */
    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    /** {@code AuditSource} name: where the event came from (HTTP, AUTH, SCHEDULED_JOB, …). */
    @Column(name = "source", length = 30)
    private String source;

    /**
     * Stable machine-readable reason, in place of an exception message. Raw {@code Throwable}
     * messages are never stored: they can quote the very row that failed, which is how a secret ends
     * up in a table meant to be safe to read.
     */
    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_path", length = 512)
    private String requestPath;

    /**
     * When the action happened, as opposed to {@link #createdAt}, which is when this row was
     * materialised. Keeping both is what makes dispatcher lag measurable rather than invisible.
     */
    @Column(name = "occurred_at")
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /** SHA-256 over the canonical event payload; the input to AR-7 tamper verification. */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
