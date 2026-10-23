package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * G9c (V85, D1): one QC decision on an APPROVED production receipt line — the production twin of
 * {@code PurchaseQualityDecision} (G7c). Never updated after insert (FE Q19). The id is assigned by the service
 * before the stock moves, so the child lots and ledger rows can point at it.
 *
 * <p>A partial decision on a tracked line carries the child lots it split off ({@code acceptedLot} AVAILABLE,
 * {@code rejectedLot} REJECTED) and their {@code LOT_SPLIT_IN} rows. A whole-receipt decision through the old
 * {@code qc-disposition} endpoint, and every decision backfilled by V85, names the receipt's own lot and no split
 * movement: the whole lot changed status. A non-tracked line carries no lot at all — accepting releases the
 * quality hold of the pool, rejecting leaves the quantity on hold, owned by the receipt line (D4).
 */
@Entity
@Table(name = "production_quality_decisions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProductionQualityDecision extends BaseEntity {

    @Id
    @Column(name = "decision_id", updatable = false, nullable = false)
    private UUID decisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false, updatable = false)
    private ProductionReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_line_id", nullable = false, updatable = false)
    private ProductionReceiptLine receiptLine;

    @Column(name = "accepted_quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal acceptedQuantity;

    @Column(name = "rejected_quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal rejectedQuantity;

    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_lot_id", updatable = false)
    private InventoryLot acceptedLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_lot_id", updatable = false)
    private InventoryLot rejectedLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_movement_id", updatable = false)
    private StockMovement acceptedMovement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_movement_id", updatable = false)
    private StockMovement rejectedMovement;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @Column(name = "decided_by", updatable = false)
    private UUID decidedBy;
}
