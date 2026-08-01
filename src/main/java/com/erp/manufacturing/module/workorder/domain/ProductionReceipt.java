package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "production_receipts", indexes = {
        @Index(name = "idx_production_receipts_work_order_id", columnList = "work_order_id"),
        @Index(name = "idx_production_receipts_status", columnList = "status"),
        @Index(name = "idx_production_receipts_posted_at", columnList = "posted_at DESC")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionReceipt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "receipt_id", updatable = false, nullable = false)
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProductionReceiptStatus status = ProductionReceiptStatus.DRAFT;

    /** Human-facing document number (spec §6.4). Null for receipts written before F5. */
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    /**
     * Business traceability, persisted on the document. Not the {@code X-Trace-Id} response
     * header — see {@code .claude/rules/error-handling.md} §5.1.
     */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    /**
     * Trace ids of the WIP output events this receipt drew from, oldest first, comma separated
     * (spec §6.4 {@code sourceWipTraceIds}). Written once at approval and only ever read whole.
     */
    @Column(name = "source_wip_trace_ids", columnDefinition = "TEXT")
    private String sourceWipTraceIds;

    /** SHA-256 of the request payload; null for documents written before V25. */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "posted_at", nullable = false)
    @Builder.Default
    private Instant postedAt = Instant.now();

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    /**
     * QC decision summary, denormalised from {@link QualityDisposition} so the list endpoint can
     * render it without loading one collection per receipt. Null until QC has ruled; non-null
     * doubles as the "already disposed" guard.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "qc_result", length = 20)
    private QualityDispositionResult qcResult;

    @Column(name = "qc_reason", columnDefinition = "TEXT")
    private String qcReason;

    @Column(name = "qc_at")
    private Instant qcAt;

    @Column(name = "qc_by")
    private UUID qcBy;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductionReceiptLine> lines = new ArrayList<>();

    public boolean isDraft() {
        return status == ProductionReceiptStatus.DRAFT;
    }

    public boolean isPendingApproval() {
        return status == ProductionReceiptStatus.PENDING_APPROVAL;
    }

    public boolean isApproved() {
        return status == ProductionReceiptStatus.APPROVED;
    }

    public boolean isQcDecided() {
        return qcResult != null;
    }

    public void submit(Instant now) {
        status = ProductionReceiptStatus.PENDING_APPROVAL;
        submittedAt = now;
    }

    /** Approval is the point where the receipt hits inventory. */
    public void approve(Instant now, UUID approver) {
        status = ProductionReceiptStatus.APPROVED;
        approvedAt = now;
        approvedBy = approver;
    }

    public void reject(Instant now, String reason) {
        status = ProductionReceiptStatus.REJECTED;
        rejectedAt = now;
        rejectReason = reason;
    }

    /**
     * Records the QC outcome. The receipt stays {@code APPROVED} — QC rules on the output lot,
     * not on the document, so rejecting quality never un-does the stock that was already received.
     */
    public void recordQcDecision(QualityDispositionResult result, String reason, Instant now, UUID actor) {
        qcResult = result;
        qcReason = reason;
        qcAt = now;
        qcBy = actor;
    }
}
