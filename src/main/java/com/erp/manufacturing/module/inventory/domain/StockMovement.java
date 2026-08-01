package com.erp.manufacturing.module.inventory.domain;

import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_warehouse_item", columnList = "warehouse_id, item_id, created_at DESC"),
        @Index(name = "idx_stock_movements_lot_id", columnList = "lot_id"),
        @Index(name = "idx_stock_movements_created_at", columnList = "created_at DESC")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "movement_id", updatable = false, nullable = false)
    private UUID movementId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private MovementDirection direction;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "reference_type", length = 80)
    private String referenceType;

    @Column(name = "reference_id", length = 120)
    private String referenceId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    /** SHA-256 of the movement command; null for movements written before V25. */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    /**
     * Business traceability (F5): links this ledger row back to the shop-floor document that caused
     * it. Not the {@code X-Trace-Id} response header, which only correlates logs — see
     * {@code .claude/rules/error-handling.md} §5.1.
     */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
