package com.erp.manufacturing.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One object touched by an audit event, persisted to {@code audit_log_entities} (AR-3/AR-5).
 *
 * <p>Exists because a single {@code entity_type}/{@code entity_id} pair on {@code audit_logs} cannot
 * describe a command that connects two objects. {@code PERMISSION_GRANTED} recorded the role and lost
 * the permission; {@code ROLE_ASSIGNED} recorded the assignment and lost which user and which scope.
 * Those are the facts an investigator actually needs, so they get rows of their own.
 *
 * <p>Named {@code ...Row} rather than {@code AuditLogEntity} on purpose: "audit log entity" reads as
 * "the entity class of the audit log", which is {@link AuditLog}, and confusing those two while
 * chasing a missing audit record is a bad half-hour.
 */
@Entity
@Table(name = "audit_log_entities")
@Immutable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntityRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_entity_id", updatable = false, nullable = false)
    private UUID auditEntityId;

    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 20)
    private com.erp.manufacturing.common.audit.model.AuditEntityRef.AuditEntityRelation relation;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @Column(name = "entity_name", length = 255)
    private String entityName;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
