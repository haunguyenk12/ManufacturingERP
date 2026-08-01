package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wip_transactions", indexes = {
        @Index(name = "idx_wip_transactions_work_order_id", columnList = "work_order_id"),
        @Index(name = "idx_wip_transactions_type", columnList = "transaction_type"),
        @Index(name = "idx_wip_transactions_occurred_at", columnList = "occurred_at DESC")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WipTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "wip_transaction_id", updatable = false, nullable = false)
    private UUID wipTransactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private WipTransactionType transactionType;

    /**
     * Operation this transaction was reported against (F5). Authoritative when present;
     * {@link #stageCode} is then derived from it. Null for work orders created without a routing,
     * where {@code stageCode} remains free text.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_operation_id")
    private WorkOrderOperation operation;

    @Column(name = "stage_code", length = 80)
    private String stageCode;

    @Column(name = "quantity", precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "reference_type", length = 80)
    private String referenceType;

    @Column(name = "reference_id", length = 120)
    private String referenceId;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
