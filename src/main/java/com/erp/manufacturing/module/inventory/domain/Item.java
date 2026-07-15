package com.erp.manufacturing.module.inventory.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "items", indexes = {
        @Index(name = "idx_items_company_id", columnList = "company_id"),
        @Index(name = "idx_items_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id", updatable = false, nullable = false)
    private UUID itemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private ItemType type;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Column(name = "lot_tracked", nullable = false)
    private boolean lotTracked;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ItemStatus status = ItemStatus.ACTIVE;

    public boolean isActive() {
        return status == ItemStatus.ACTIVE;
    }

    public void deactivate() {
        status = ItemStatus.INACTIVE;
    }
}
