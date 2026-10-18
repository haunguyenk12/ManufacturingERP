package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "goods_receipts", indexes = {
        @Index(name = "idx_goods_receipts_po_status_posted", columnList = "purchase_order_id, status, posted_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceipt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "goods_receipt_id", updatable = false, nullable = false)
    private UUID goodsReceiptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "receipt_no", nullable = false, length = 100)
    private String receiptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GoodsReceiptStatus status = GoodsReceiptStatus.POSTED;

    /** {@code null} while DRAFT (V77); always set once POSTED ({@code chk_goods_receipts_posted_at}). */
    @Column(name = "posted_at")
    private Instant postedAt;

    /** The legacy create-and-post path requires it; a draft created through G7b may have none. */
    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    /** G7b: business date of the delivery in the plant's time zone (not {@code postedAt}). */
    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "reference", length = 200)
    private String reference;

    @Column(name = "posted_by")
    private UUID postedBy;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    /**
     * G7b (D4): posted on hold — stock without a lot sits in {@code qualityHoldQuantity}. Receipts
     * posted before G7b went to free stock; reversing one must not release a hold it never made.
     */
    @Column(name = "quality_held", nullable = false)
    @Builder.Default
    private boolean qualityHeld = false;

    /** SHA-256 of the request payload; null for documents written before V25. */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_note", columnDefinition = "TEXT")
    private String cancelNote;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GoodsReceiptLine> lines = new ArrayList<>();

    public boolean isPosted() {
        return status == GoodsReceiptStatus.POSTED;
    }

    public boolean isDraft() {
        return status == GoodsReceiptStatus.DRAFT;
    }

    public void markPosted(Instant at, UUID by) {
        this.status = GoodsReceiptStatus.POSTED;
        this.qualityHeld = true;
        this.postedAt = at;
        this.postedBy = by;
    }

    public void cancel(String note) {
        cancel(note, Instant.now(), null);
    }

    public void cancel(String note, Instant at, UUID by) {
        this.status = GoodsReceiptStatus.CANCELLED;
        this.cancelledAt = at;
        this.cancelledBy = by;
        this.cancelNote = note;
    }
}
