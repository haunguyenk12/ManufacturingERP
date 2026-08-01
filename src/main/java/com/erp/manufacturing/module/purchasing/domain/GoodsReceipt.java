package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
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

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

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

    public void cancel(String note) {
        this.status = GoodsReceiptStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancelNote = note;
    }
}
