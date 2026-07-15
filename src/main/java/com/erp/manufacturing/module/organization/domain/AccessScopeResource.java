package com.erp.manufacturing.module.organization.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_scope_resources", indexes = {
        @Index(name = "idx_access_scope_resources_scope_id", columnList = "scope_id"),
        @Index(name = "idx_access_scope_resources_resource", columnList = "resource_type, resource_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessScopeResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "scope_resource_id", updatable = false, nullable = false)
    private UUID scopeResourceId;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private ScopeResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
