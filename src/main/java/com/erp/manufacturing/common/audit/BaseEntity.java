package com.erp.manufacturing.common.audit;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base entity providing JPA auditing fields for all business entities.
 * <p>
 * Requires:
 * <ul>
 *   <li>{@code @EnableJpaAuditing} on a config class</li>
 *   <li>{@code AuditorAware<UUID>} bean named "auditorAware"</li>
 * </ul>
 * All subclass tables must include columns:
 * {@code created_at}, {@code updated_at}, {@code created_by}, {@code updated_by}, {@code version}.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version")
    private Long version;
}
