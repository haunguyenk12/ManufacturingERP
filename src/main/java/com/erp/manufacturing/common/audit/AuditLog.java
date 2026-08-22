package com.erp.manufacturing.common.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

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
        @Index(name = "idx_audit_plant_id",   columnList = "plant_id")
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

    @Column(name = "trace_id", length = 32)
    private String traceId;

    /**
     * C2-1: added by {@code V54}, nullable, never backfilled — every historical row is {@code null},
     * and (scope decision) so is every new row until a follow-up phase wires real-time population
     * from the audited call sites. Filtering by it today is a correct no-op, not a bug.
     */
    @Column(name = "plant_id")
    private UUID plantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
