package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "material_reservations", indexes = {
        @Index(name = "idx_material_reservations_work_order_id", columnList = "work_order_id"),
        @Index(name = "idx_material_reservations_component_line_id", columnList = "component_line_id"),
        @Index(name = "idx_material_reservations_item_id", columnList = "item_id"),
        @Index(name = "idx_material_reservations_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "idx_material_reservations_lot_id", columnList = "lot_id"),
        @Index(name = "idx_material_reservations_status", columnList = "status")
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_line_id", nullable = false)
    private WorkOrderComponentLine componentLine;

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
