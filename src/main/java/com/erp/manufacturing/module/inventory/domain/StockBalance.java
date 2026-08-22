package com.erp.manufacturing.module.inventory.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stock_balances", indexes = {
        @Index(name = "idx_stock_balances_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "idx_stock_balances_item_id", columnList = "item_id"),
        @Index(name = "idx_stock_balances_lot_id", columnList = "lot_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBalance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "balance_id", updatable = false, nullable = false)
    private UUID balanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reserved_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    /**
     * On-hand output that has not passed quality yet. Lot-tracked stock carries the same gate on
     * {@link InventoryLot#getStatus()}; this column is the equivalent carrier for stock that has no
     * lot row. It is deliberately separate from {@code reservedQuantity}: quality hold is not a
     * demand allocation and must be visible as such to inventory clients.
     */
    @Column(name = "quality_hold_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal qualityHoldQuantity = BigDecimal.ZERO;

    public BigDecimal availableQuantity() {
        return quantity.subtract(reservedQuantity).subtract(qualityHoldQuantity);
    }

    public void increase(BigDecimal amount) {
        quantity = quantity.add(amount);
    }

    public void decrease(BigDecimal amount) {
        quantity = quantity.subtract(amount);
    }

    public void reserve(BigDecimal amount) {
        reservedQuantity = reservedQuantity.add(amount);
    }

    public void releaseReserved(BigDecimal amount) {
        reservedQuantity = reservedQuantity.subtract(amount);
    }

    public void holdForQuality(BigDecimal amount) {
        qualityHoldQuantity = qualityHoldQuantity.add(amount);
    }

    public void releaseQualityHold(BigDecimal amount) {
        qualityHoldQuantity = qualityHoldQuantity.subtract(amount);
    }

    public void consumeReserved(BigDecimal amount) {
        reservedQuantity = reservedQuantity.subtract(amount);
        quantity = quantity.subtract(amount);
    }
}
