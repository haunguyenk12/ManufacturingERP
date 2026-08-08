package com.erp.manufacturing.module.organization.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "plants", indexes = {
        @Index(name = "idx_plants_company_id", columnList = "company_id"),
        @Index(name = "idx_plants_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plant extends BaseEntity {

    public static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "plant_id", updatable = false, nullable = false)
    private UUID plantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "timezone", nullable = false, length = 100)
    @Builder.Default
    private String timezone = DEFAULT_TIMEZONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    public void deactivate() {
        status = OrganizationStatus.INACTIVE;
    }

    public void activate() {
        status = OrganizationStatus.ACTIVE;
    }
}
