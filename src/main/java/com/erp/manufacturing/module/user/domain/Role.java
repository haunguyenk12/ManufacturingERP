package com.erp.manufacturing.module.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Role entity (RBAC).
 * Role names should be plain without prefix: ADMIN, MANAGER, OPERATOR.
 * Spring Security authority will be prefixed with ROLE_ at the service level.
 */
@Entity
@Table(name = "roles")
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

    @Column(name = "name", unique = true, nullable = false, length = 50)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
