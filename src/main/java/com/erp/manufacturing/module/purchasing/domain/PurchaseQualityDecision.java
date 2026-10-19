package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * G7c (V78): one purchase QC decision on a posted, on-hold goods receipt line (D5). Never updated
 * after insert — there are no setters, and correcting a decision is out of scope (FE §1.2). The id
 * is assigned by the service before the stock moves, so the child lots and ledger rows can point at it.
 *
 * <p>Tracked lines carry the child lots the decision split off ({@code acceptedLot} AVAILABLE,
 * {@code rejectedLot} REJECTED) and their {@code LOT_SPLIT_IN} rows. Non-tracked lines carry none:
 * accepting releases the quality hold of the shared pool, rejecting leaves the quantity on hold; the
 * goods receipt line is the owner of that hold ({@code qualityBucketId} = line id, D7).
 */
@Entity
@Table(name = "purchase_quality_decisions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PurchaseQualityDecision extends BaseEntity {

    @Id
    @Column(name = "decision_id", updatable = false, nullable = false)
    private UUID decisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false, updatable = false)
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_line_id", nullable = false, updatable = false)
    private GoodsReceiptLine goodsReceiptLine;

    @Column(name = "accepted_quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal acceptedQuantity;

    @Column(name = "rejected_quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal rejectedQuantity;

    @Column(name = "reason", length = 500, updatable = false)
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
