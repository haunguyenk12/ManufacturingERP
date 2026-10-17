package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stock held out of free availability for one owner: a work order component line (F5) or, since
 * G6a, a sales order line. {@code chk_material_reservations_single_owner} (V74) enforces exactly one
 * owner per row. The held amount itself lives on {@code stock_balances.reserved_quantity}, so every
 * availability figure in the system sees both kinds without knowing they exist.
 *
 * <p>The sales owner is two plain UUID columns, not {@code @ManyToOne}s, for the same reason as
 * {@link WorkOrderDemandAllocation#getSalesOrderLineId()}: a typed association would close the
 * {@code sales → planning → workorder → sales} compile-time cycle. The foreign keys exist in the DB.
 */
@Entity
@Table(name = "material_reservations", indexes = {
        @Index(name = "idx_material_reservations_work_order_id", columnList = "work_order_id"),
        @Index(name = "idx_material_reservations_component_line_id", columnList = "component_line_id"),
        @Index(name = "idx_material_reservations_item_id", columnList = "item_id"),
        @Index(name = "idx_material_reservations_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "idx_material_reservations_lot_id", columnList = "lot_id"),
        @Index(name = "idx_material_reservations_status", columnList = "status"),
        @Index(name = "idx_material_reservations_sales_order_id", columnList = "sales_order_id"),
        @Index(name = "idx_material_reservations_sales_order_line_id", columnList = "sales_order_line_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialReservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reservation_id", updatable = false, nullable = false)
    private UUID reservationId;

    /** {@code null} for a sales-order reservation (G6a). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id")
    private WorkOrder workOrder;

    /** {@code null} for a sales-order reservation (G6a). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_line_id")
    private WorkOrderComponentLine componentLine;

    /** Set only for a sales-order reservation (G6a); see the class comment. */
    @Column(name = "sales_order_id")
    private UUID salesOrderId;

    @Column(name = "sales_order_line_id")
    private UUID salesOrderLineId;

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
    private BigDecimal quantity;

    @Column(name = "consumed_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MaterialReservationStatus status = MaterialReservationStatus.ACTIVE;

    public boolean isForSalesOrder() {
        return salesOrderLineId != null;
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(consumedQuantity);
    }

    public void consume(BigDecimal amount) {
        consumedQuantity = consumedQuantity.add(amount);
        if (remainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            status = MaterialReservationStatus.CONSUMED;
        }
    }

    public void release() {
        status = MaterialReservationStatus.RELEASED;
    }

    public void cancel() {
        status = MaterialReservationStatus.CANCELLED;
    }
}
