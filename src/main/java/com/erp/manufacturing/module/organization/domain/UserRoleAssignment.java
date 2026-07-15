package com.erp.manufacturing.module.organization.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_role_assignments", indexes = {
        @Index(name = "idx_user_role_assignments_user_id", columnList = "user_id"),
        @Index(name = "idx_user_role_assignments_role_id", columnList = "role_id"),
        @Index(name = "idx_user_role_assignments_scope_id", columnList = "scope_id"),
        @Index(name = "idx_user_role_assignments_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id", updatable = false, nullable = false)
    private UUID assignmentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public void deactivate() {
        status = AssignmentStatus.INACTIVE;
    }
}
