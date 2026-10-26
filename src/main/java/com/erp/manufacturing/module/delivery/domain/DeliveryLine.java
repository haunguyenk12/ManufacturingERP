package com.erp.manufacturing.module.delivery.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G10b: one stock position of a delivery (V87). Cross-module links are plain UUIDs, like the rest of this module.
 * {@code reservedQuantity} and the two movement ids are set at POST: the part taken from the order's own holds and the
 * part taken from free stock are two separate {@code ISSUE} movements.
 */
@Entity
@Table(name = "delivery_lines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "delivery_line_id", updatable = false, nullable = false)
    private UUID deliveryLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, updatable = false)
    private Delivery delivery;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "sales_order_line_id", nullable = false)
    private UUID salesOrderLineId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    /** {@code null} for an item that is not lot-tracked: the line then takes from the warehouse's untracked pool. */
    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "reserved_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "reserved_movement_id")
    private UUID reservedMovementId;

    @Column(name = "free_movement_id")
    private UUID freeMovementId;
}
