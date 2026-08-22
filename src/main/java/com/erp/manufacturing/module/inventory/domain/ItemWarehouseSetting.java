package com.erp.manufacturing.module.inventory.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "item_warehouse_settings", indexes = {
        @Index(name = "idx_item_wh_settings_item_id", columnList = "item_id"),
        @Index(name = "idx_item_wh_settings_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "idx_item_wh_settings_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemWarehouseSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "setting_id", updatable = false, nullable = false)
    private UUID settingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "safety_stock", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal safetyStock = BigDecimal.ZERO;

    @Column(name = "reorder_point", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal reorderPoint = BigDecimal.ZERO;

    @Column(name = "lead_time_days", nullable = false)
    @Builder.Default
    private Integer leadTimeDays = 0;

    @Column(name = "is_default_supply", nullable = false)
    @Builder.Default
    private boolean defaultSupply = false;

    @Column(name = "is_default_output", nullable = false)
    @Builder.Default
    private boolean defaultOutput = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ItemWarehouseSettingStatus status = ItemWarehouseSettingStatus.ACTIVE;

    public void activate() {
        status = ItemWarehouseSettingStatus.ACTIVE;
    }

    public void deactivate() {
        status = ItemWarehouseSettingStatus.INACTIVE;
    }
}
