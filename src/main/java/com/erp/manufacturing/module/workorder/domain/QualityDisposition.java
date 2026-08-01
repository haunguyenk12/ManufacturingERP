package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-lot audit trail of the QC decision taken on an approved production receipt.
 * One row per distinct output lot; the receipt-level summary lives on
 * {@link ProductionReceipt#getQcResult()}.
 *
 * <p><b>Not every QC decision appears here.</b> Since D5, output that is not lot-tracked can also be
 * dispositioned, and those verdicts exist only on the receipt — a row with no lot would contradict
 * this table's own definition, so none is written. Read {@code production_receipts} instead when a
 * report has to cover every decision.
 */
@Entity
@Table(name = "quality_dispositions", indexes = {
        @Index(name = "idx_quality_dispositions_receipt_id", columnList = "receipt_id"),
        @Index(name = "idx_quality_dispositions_lot_id", columnList = "lot_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityDisposition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "disposition_id", updatable = false, nullable = false)
    private UUID dispositionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private ProductionReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private InventoryLot lot;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private QualityDispositionResult result;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
