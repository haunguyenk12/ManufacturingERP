package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * One shop-floor report: what an operator produced, scrapped, and sent to rework.
 * <p>
 * This is the document that moves a work order forward in F5. It lives in {@code module/workorder}
 * for the same reason {@link QualityDisposition} does — it belongs to the Work Order aggregate and
 * its endpoints hang off {@code /work-orders/{id}} — not because "production execution" is a
 * sub-concept of work orders.
 */
@Entity
@Table(name = "production_executions", indexes = {
        @Index(name = "idx_production_executions_work_order_id", columnList = "work_order_id"),
        @Index(name = "idx_production_executions_operation_id", columnList = "work_order_operation_id"),
        @Index(name = "idx_production_executions_created_at", columnList = "created_at DESC")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionExecution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "production_execution_id", updatable = false, nullable = false)
    private UUID productionExecutionId;

    /**
     * Human-facing document number (spec §5.2 "History"), {@code PE-} + 8 hex of the id.
     *
     * <p>Spec calls it "Mã WIP", but {@code wip_transactions} is a different ledger table with no
     * code of its own, so a {@code WIP-} prefix here would read as identifying one of those rows.
     */
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    /** Null when the work order has no routing snapshot to report against. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_operation_id")
    private WorkOrderOperation operation;

    @Column(name = "good_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal goodQuantity = BigDecimal.ZERO;

    @Column(name = "scrap_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal scrapQuantity = BigDecimal.ZERO;

    @Column(name = "rework_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal reworkQuantity = BigDecimal.ZERO;

    @Column(name = "actual_started_at")
    private Instant actualStartedAt;

    @Column(name = "actual_ended_at")
    private Instant actualEndedAt;

    @Column(name = "operator_user_id")
    private UUID operatorUserId;

    /**
     * Business traceability, persisted on the document. Distinct from the {@code X-Trace-Id}
     * response header, which only correlates logs — see {@code .claude/rules/error-handling.md} §5.1.
     */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Derives the document number from the row's own id.
     *
     * <p>Must run as {@code @PrePersist} and not after {@code save()}: Hibernate snapshots the
     * entity when it queues the insert, so a field set afterwards is simply absent from the INSERT
     * and the NOT NULL column blows up (CLAUDE.md §0.12 #5).
     *
     * <p>Must stay the same formula as the {@code V40} backfill
     * ({@code 'PE-' || upper(substring(production_execution_id::text, 1, 8))}).
     */
    @PrePersist
    public void assignCode() {
        if (code == null) {
            code = "PE-" + productionExecutionId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
    }
}
