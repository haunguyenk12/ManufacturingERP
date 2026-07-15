package com.erp.manufacturing.common.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Immutable;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log_changes", indexes = {
        @Index(name = "idx_audit_log_changes_audit_id", columnList = "audit_id"),
        @Index(name = "idx_audit_log_changes_field_name", columnList = "field_name")
})
@Immutable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "change_id", updatable = false, nullable = false)
    private UUID changeId;

    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "field_name", nullable = false, length = 150)
    private String fieldName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private AuditLogChangeType changeType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
