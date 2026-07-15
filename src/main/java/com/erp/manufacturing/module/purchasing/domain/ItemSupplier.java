package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "item_suppliers", indexes = {
        @Index(name = "idx_item_suppliers_item_status_preferred", columnList = "item_id, status, preferred"),
        @Index(name = "idx_item_suppliers_supplier_id", columnList = "supplier_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSupplier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_supplier_id", updatable = false, nullable = false)
    private UUID itemSupplierId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "supplier_item_code", length = 120)
    private String supplierItemCode;

    @Column(name = "lead_time_days", nullable = false)
    @Builder.Default
    private Integer leadTimeDays = 0;

    @Column(name = "minimum_order_quantity", precision = 19, scale = 6)
    private BigDecimal minimumOrderQuantity;

    @Column(name = "unit_price", precision = 19, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "preferred", nullable = false)
    @Builder.Default
    private boolean preferred = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ItemSupplierStatus status = ItemSupplierStatus.ACTIVE;

    public boolean isActive() {
        return status == ItemSupplierStatus.ACTIVE;
    }

    public void deactivate() {
        status = ItemSupplierStatus.INACTIVE;
        preferred = false;
    }
}
