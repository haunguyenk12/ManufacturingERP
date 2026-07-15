package com.erp.manufacturing.module.organization.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Role entity — RBAC role thuộc về organization module.
 *
 * <p>Global/system roles keep {@code companyId == null}. Custom roles may be
 * scoped to a company, while legacy auth still reads {@code user_roles}.
 * Role names should be plain without prefix: ADMIN, MANAGER, OPERATOR.
 * Spring Security authority will be prefixed with ROLE_ at the service level.
 */
@Entity
@Table(name = "roles", indexes = {
        @Index(name = "idx_roles_company_id", columnList = "company_id"),
        @Index(name = "idx_roles_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "role_id", updatable = false, nullable = false)
    private UUID roleId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean system = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RoleStatus status = RoleStatus.ACTIVE;

    public boolean isActive() {
        return status == RoleStatus.ACTIVE;
    }
}
