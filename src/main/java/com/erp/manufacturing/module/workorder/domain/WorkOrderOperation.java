package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A routing operation frozen onto the work order at creation time (spec §3.3).
 * <p>
 * {@code sourceRoutingOperationId} is a plain UUID, not a {@code @ManyToOne}, for the same reason
 * {@code WorkOrder.sourceRoutingId} is: navigating the association would return live master data
 * and quietly break the snapshot contract (invariants B49 / B53). Name, work centre, and minutes
 * are copies — revising the routing master must never change a work order that is already running.
 */
@Entity
@Table(name = "work_order_operations", indexes = {
        @Index(name = "idx_work_order_operations_work_order_id", columnList = "work_order_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderOperation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "work_order_operation_id", updatable = false, nullable = false)
    private UUID workOrderOperationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    /** Provenance of the snapshot. Never dereferenced to read operation data. */
    @Column(name = "source_routing_operation_id")
    private UUID sourceRoutingOperationId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "work_center_code", nullable = false, length = 100)
    private String workCenterCode;

    /**
     * Live FK to the Work Center this operation runs on (C2-8), alongside — not replacing —
     * {@link #workCenterCode}. Nullable: rows created before this phase have no work center to
     * point at ({@code RoutingOperation.workCenter} was itself nullable pre-C2-6, see
     * {@code module/workcenter/CLAUDE.md} bất biến B_wc2), and are simply invisible to the Capacity
     * Board as a result — not backfilled, same shape as that decision.
     *
     * <p>This reverses {@code module/workcenter/CLAUDE.md} mục 4 ("KHÔNG đổi thành FK"): that call
     * was made before there was a real consumer. The Capacity Board (C2-8) is that consumer — it
     * must join/filter by Work Center and read its {@code capacityUnits}/{@code workCalendar}, which
     * a free-text code cannot support. {@link #workCenterCode} stays as the immutable display
     * snapshot; this FK exists purely so the capacity engine can locate the work center.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_center_id")
    private WorkCenter workCenter;

    @Column(name = "setup_minutes", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal setupMinutes = BigDecimal.ZERO;

    @Column(name = "run_minutes_per_unit", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal runMinutesPerUnit = BigDecimal.ZERO;

    /**
     * Generated once, at {@code WorkOrder.release()} (C2-8 decision #1) — null before that. See
     * {@code module/workorder/CLAUDE.md} for the scheduling algorithm and its invariants.
     */
    @Column(name = "planned_start_at")
    private Instant plannedStartAt;

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    /** Last-reason-wins, same shape as {@code WorkOrder.blockReason}/{@code cancelReason}. */
    @Column(name = "schedule_adjustment_reason", columnDefinition = "TEXT")
    private String scheduleAdjustmentReason;

    /** Stage label used on the WIP ledger when a transaction is reported against this operation. */
    public String stageCode() {
        return sequence + "-" + workCenterCode;
    }
}
