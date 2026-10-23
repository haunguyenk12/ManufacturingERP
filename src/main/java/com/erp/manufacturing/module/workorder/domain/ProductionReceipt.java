package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    /** Cut to microseconds, what Postgres keeps: the instant a command answers must be the one read back (G9a). */
    @Column(name = "posted_at", nullable = false)
    @Builder.Default
    private Instant postedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** G9a (V83): business date of the receipt, plant-local; never after the plant's today. */
    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    /** G9a (V83): shelf-life end of the output lot (lot-tracked only); the lot expires at its start, plant-local. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    /** The last withdrawal back to DRAFT (G9a); a receipt can be withdrawn and submitted again. */
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;

    @Column(name = "withdraw_reason", columnDefinition = "TEXT")
    private String withdrawReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

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

    /** G9c (D2): QC is over once nothing is pending — no longer "one verdict was recorded". */
    public boolean isQcDecided() {
        return pendingQcQuantity(status, lines).signum() == 0;
    }

    public ProductionQualityState qualityState() {
        return qualityState(status, lines);
    }

    /** Sum of what QC accepted on the given lines (the receipt's own, or a batch-loaded copy of them — C14). */
    public static BigDecimal qcAcceptedQuantity(List<ProductionReceiptLine> lines) {
        return lines.stream().map(ProductionReceiptLine::getQcAcceptedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal qcRejectedQuantity(List<ProductionReceiptLine> lines) {
        return lines.stream().map(ProductionReceiptLine::getQcRejectedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** What QC still has to judge; 0 for a receipt that is not APPROVED (FE §3.1). */
    public static BigDecimal pendingQcQuantity(ProductionReceiptStatus status, List<ProductionReceiptLine> lines) {
        return status == ProductionReceiptStatus.APPROVED
                ? lines.stream().map(ProductionReceiptLine::pendingQcQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;
    }

    /**
     * G9c (FE Q20): the quality state, independent of the document status; {@code null} before approval.
     * Something still pending is PARTIAL even when part was rejected — the totals keep the rejection visible.
     */
    public static ProductionQualityState qualityState(ProductionReceiptStatus status, List<ProductionReceiptLine> lines) {
        if (status != ProductionReceiptStatus.APPROVED) {
            return null;
        }
        BigDecimal accepted = qcAcceptedQuantity(lines);
        BigDecimal rejected = qcRejectedQuantity(lines);
        if (accepted.signum() == 0 && rejected.signum() == 0) {
            return ProductionQualityState.NOT_STARTED;
        }
        if (pendingQcQuantity(status, lines).signum() > 0) {
            return ProductionQualityState.PARTIAL;
        }
        if (rejected.signum() == 0) {
            return ProductionQualityState.ACCEPTED;
        }
        return accepted.signum() == 0 ? ProductionQualityState.REJECTED : ProductionQualityState.MIXED;
    }

    /** DRAFT and PENDING_APPROVAL still claim output of the work order (B16); every other status does not. */
    public boolean isOpen() {
        return status == ProductionReceiptStatus.DRAFT || status == ProductionReceiptStatus.PENDING_APPROVAL;
    }

    public void submit(Instant now, UUID actor) {
        status = ProductionReceiptStatus.PENDING_APPROVAL;
        submittedAt = now;
        submittedBy = actor;
    }

    /** Back to DRAFT so the requester can edit it; the output it claims stays claimed (FE R04). */
    public void withdraw(Instant now, UUID actor, String reason) {
        status = ProductionReceiptStatus.DRAFT;
        withdrawnAt = now;
        withdrawnBy = actor;
        withdrawReason = reason;
    }

    /** Terminal: the output it claimed is free again, nothing was ever posted. */
    public void cancel(Instant now, UUID actor, String reason) {
        status = ProductionReceiptStatus.CANCELLED;
        cancelledAt = now;
        cancelledBy = actor;
        cancelReason = reason;
    }

    /** Approval is the point where the receipt hits inventory. */
    public void approve(Instant now, UUID approver) {
        status = ProductionReceiptStatus.APPROVED;
        approvedAt = now;
        approvedBy = approver;
    }

    public void reject(Instant now, UUID actor, String reason) {
        status = ProductionReceiptStatus.REJECTED;
        rejectedAt = now;
        rejectedBy = actor;
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

    /**
     * G9c (D2): after a decision has been added to the lines, refresh the summary the old fields carried — the
     * latest decision's reason, time and actor, and a whole-receipt result only once QC ended all one way
     * ({@code AVAILABLE} for ACCEPTED, {@code REJECTED} for REJECTED, {@code null} while PARTIAL or when MIXED).
     */
    public void summarizeQualityDecision(String reason, Instant now, UUID actor) {
        ProductionQualityState state = qualityState();
        qcResult = state == ProductionQualityState.ACCEPTED ? QualityDispositionResult.AVAILABLE
                : state == ProductionQualityState.REJECTED ? QualityDispositionResult.REJECTED
                : null;
        qcReason = reason;
        qcAt = now;
        qcBy = actor;
    }
}
