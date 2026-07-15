package com.erp.manufacturing.module.organization.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "warehouses", indexes = {
        @Index(name = "idx_warehouses_plant_id", columnList = "plant_id"),
        @Index(name = "idx_warehouses_status", columnList = "status"),
        @Index(name = "idx_warehouses_type", columnList = "type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Warehouse extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "warehouse_id", updatable = false, nullable = false)
    private UUID warehouseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    @Builder.Default
    private WarehouseType type = WarehouseType.GENERAL;

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
}
