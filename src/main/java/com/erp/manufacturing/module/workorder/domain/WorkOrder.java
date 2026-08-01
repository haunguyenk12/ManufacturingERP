package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "work_orders", indexes = {
        @Index(name = "idx_work_orders_company_id", columnList = "company_id"),
        @Index(name = "idx_work_orders_plant_id", columnList = "plant_id"),
        @Index(name = "idx_work_orders_product_item_id", columnList = "product_item_id"),
        @Index(name = "idx_work_orders_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "work_order_id", updatable = false, nullable = false)
    private UUID workOrderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "work_order_no", nullable = false, length = 100)
    private String workOrderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_item_id", nullable = false)
    private Item productItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_id", nullable = false)
    private BomHeader bom;

    @Column(name = "bom_revision", nullable = false, length = 40)
    private String bomRevision;

    /**
     * Routing snapshot (spec §3.3). Plain copied columns, deliberately not a {@code @ManyToOne} —
     * reading through an association would return live master data and silently break the
     * "snapshot is frozen at creation" invariant (B49). Null on work orders created before F4 and
     * on manually created ones whose item has no ACTIVE routing.
     */
    @Column(name = "source_routing_id")
    private UUID sourceRoutingId;

    @Column(name = "source_routing_code", length = 100)
    private String sourceRoutingCode;

    @Column(name = "source_routing_version", length = 40)
    private String sourceRoutingVersion;

    @Column(name = "routing_captured_at")
    private Instant routingCapturedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "output_warehouse_id", nullable = false)
    private Warehouse outputWarehouse;

    @Column(name = "planned_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal plannedQuantity;

    /**
     * How much has been <em>receipted into stock</em>. Deliberately not renamed in F5: the column
     * already holds that meaning for every existing row, and renaming it would silently reinterpret
     * historical data (NEXT_PHASE_PLAN F5 §1.2 item 6). What the shop floor produced lives in
     * {@link #actualGoodQuantity}.
     */
    @Column(name = "completed_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal completedQuantity = BigDecimal.ZERO;

    /**
     * Cumulative good quantity reported by Production Execution. This — not the receipt — is what
     * drives the work order to {@code COMPLETED} (spec §5, CLAUDE.md §0.5).
     */
    @Column(name = "actual_good_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal actualGoodQuantity = BigDecimal.ZERO;

    @Column(name = "actual_scrap_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal actualScrapQuantity = BigDecimal.ZERO;

    @Column(name = "actual_rework_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal actualReworkQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WorkOrderStatus status = WorkOrderStatus.DRAFT;

    @Column(name = "planned_start_at")
    private Instant plannedStartAt;

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    /**
     * When the shop floor first reported against this work order (spec §5.3, V39). Set by
     * {@link #reportProduction} on the first report and never moved afterwards — it is the opening
     * bracket whose closing bracket is {@link #completedAt}.
     *
     * <p>Null means either "no production has been reported yet" or "reported before F8". Those are
     * not distinguishable, and deliberately so: inventing a timestamp for historical rows would put
     * a fabricated audit trail into history (same call V38 made for {@code cancel_reason}).
     */
    @Column(name = "execution_started_at")
    private Instant executionStartedAt;

    /**
     * When the shop floor finished, i.e. when cumulative good reached the plan. Spec §5.3 calls this
     * {@code executionCompletedAt}; this column already <em>is</em> that instant, because
     * {@link #complete(Instant)} has exactly one call site ({@link #reportProduction}) and invariant
     * B53 pins it there. A second column carrying the same value was deliberately not added in F8 —
     * it would be a denormalisation that goes wrong the first time somebody adds a call site.
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "block_reason", columnDefinition = "TEXT")
    private String blockReason;

    /**
     * Why this work order was cancelled (spec §3.2, V38). {@code null} means the work order was
     * cancelled before F7 made the reason mandatory — <em>not</em> that no reason was given.
     */
    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Where this work order came from in planning (spec §3.3 "Nguồn gốc", V39). Plain nullable
     * columns rather than a {@code @ManyToOne} into {@code planning} — the same call
     * {@link WorkOrderDemandAllocation#getSalesOrderLineId()} makes, and for the same reason:
     * {@code sales → planning → workorder} already exists, so a typed association back into planning
     * would close a compile-time dependency cycle.
     *
     * <p>{@code planningRunCode} is denormalised on purpose, exactly like {@link #sourceRoutingCode}
     * (B49): it records what the run was called when this work order was created, not what it is
     * called now.
     *
     * <p>Null on manually created work orders — which is correct, they have no MRP origin — and on
     * everything created before F8.
     */
    @Column(name = "planning_run_id")
    private UUID planningRunId;

    @Column(name = "planning_run_code", length = 40)
    private String planningRunCode;

    @Column(name = "planning_proposal_id")
    private UUID planningProposalId;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<WorkOrderComponentLine> componentLines = new ArrayList<>();

    /** Routing operations frozen at creation time (invariant B53). Empty when there is no routing. */
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<WorkOrderOperation> operations = new ArrayList<>();

    public boolean isDraft() {
        return status == WorkOrderStatus.DRAFT;
    }

    public boolean canExecute() {
        return status == WorkOrderStatus.RELEASED || status == WorkOrderStatus.IN_PROGRESS;
    }

    /**
     * Reservation is deliberately <b>not</b> gated by {@link #canExecute()} (D9, debt #22).
     * Reserving writes no ledger entry — it only moves quantity from available to reserved on the
     * stock balance — and gate 1a refuses to release until every component is reserved, so requiring
     * {@code RELEASED} to reserve made the two rules deadlock each other: neither could go first.
     * Everything up to the point the work order stops being alive may reserve.
     */
    public boolean canReserve() {
        return status != WorkOrderStatus.COMPLETED && status != WorkOrderStatus.CANCELLED;
    }

    /**
     * Warehousing output is deliberately <b>not</b> gated by {@link #canExecute()} (D11, debt #25).
     * {@link #reportProduction} completes the work order the moment cumulative good reaches the plan
     * (B53), but {@code completedQuantity} — how much has actually been receipted — lags behind it,
     * so {@code canExecute()} shut the door on the last receipt of every work order whose plan was
     * exactly what had to be warehoused. {@code COMPLETED} means the shop floor is done, not that the
     * goods are in the racks; the two are separate columns on purpose (CLAUDE.md §0.5).
     *
     * <p>This does not widen how much may be receipted: the ceiling is still
     * {@link #availableToReceipt()} minus open receipts (B16). {@code CANCELLED} and {@code BLOCKED}
     * stay refused, exactly as under {@code canExecute()}.
     */
    public boolean canReceipt() {
        return canExecute() || status == WorkOrderStatus.COMPLETED;
    }

    /**
     * A work order may be released from {@code DRAFT}, {@code PLANNED}, or {@code BLOCKED}
     * (blocked = release was previously refused because material reservation was incomplete).
     */
    public boolean canRelease() {
        return status == WorkOrderStatus.DRAFT
                || status == WorkOrderStatus.PLANNED
                || status == WorkOrderStatus.BLOCKED;
    }

    /** Only an untouched draft can be scheduled; anything further along is already committed. */
    public boolean canPlan() {
        return status == WorkOrderStatus.DRAFT;
    }

    /**
     * How much more the shop floor may still report as good before hitting the plan.
     */
    public BigDecimal remainingPlannedQuantity() {
        return plannedQuantity.subtract(actualGoodQuantity);
    }

    /**
     * Invariant B16 (rewritten in F5): a receipt draws from what production actually made, not from
     * what was planned. Open receipts still have to be deducted by the caller.
     */
    public BigDecimal availableToReceipt() {
        return actualGoodQuantity.subtract(completedQuantity);
    }

    /**
     * How far the <em>shop floor</em> has got, as a percentage (spec §5.2 "Summary"). Derived from
     * {@link #actualGoodQuantity} over {@link #plannedQuantity} — the same pair
     * {@link #remainingPlannedQuantity()} works on, so the Summary panel is internally consistent.
     *
     * <p>Not to be confused with anything derived from {@link #completedQuantity}: that measures how
     * much has been <em>warehoused</em>, which lags behind and is a different number
     * (CLAUDE.md §0.5).
     *
     * <p>Scale 2 / {@code HALF_UP} is part of the contract, not an implementation detail — the
     * frontend renders this straight into a progress badge. A planned quantity of zero cannot occur
     * through the API ({@code requirePositive}), but is guarded anyway because dividing by it would
     * throw rather than return a wrong number.
     */
    public BigDecimal completionPercent() {
        if (plannedQuantity == null || plannedQuantity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return actualGoodQuantity
                .multiply(BigDecimal.valueOf(100))
                .divide(plannedQuantity, 2, RoundingMode.HALF_UP);
    }

    /**
     * Marks the work order as waiting for material. Persisted so planners can query
     * "which work orders are short on reservation" without replaying the release attempt.
     */
    public void block(Instant now, String reason) {
        status = WorkOrderStatus.BLOCKED;
        blockedAt = now;
        blockReason = reason;
    }

    public void plan() {
        status = WorkOrderStatus.PLANNED;
    }

    /**
     * Applies one shop-floor report. Completion is decided here and only here — the production
     * receipt no longer advances the work order (CLAUDE.md §0.5).
     *
     * <p>Spec §5.3: the <em>first</em> report opens {@link #executionStartedAt} and later reports
     * leave it alone — it marks when production began, not when it was last touched.
     */
    public void reportProduction(BigDecimal good, BigDecimal scrap, BigDecimal rework, Instant now) {
        if (executionStartedAt == null) {
            executionStartedAt = now;
        }
        actualGoodQuantity = actualGoodQuantity.add(good);
        actualScrapQuantity = actualScrapQuantity.add(scrap);
        actualReworkQuantity = actualReworkQuantity.add(rework);
        markInProgress();
        if (actualGoodQuantity.compareTo(plannedQuantity) >= 0) {
            complete(now);
        }
    }

    public void release(Instant now) {
        status = WorkOrderStatus.RELEASED;
        releasedAt = now;
        blockedAt = null;
        blockReason = null;
    }

    public void markInProgress() {
        if (status == WorkOrderStatus.RELEASED) {
            status = WorkOrderStatus.IN_PROGRESS;
        }
    }

    public void complete(Instant now) {
        status = WorkOrderStatus.COMPLETED;
        completedAt = now;
    }

    public void cancel(Instant now, String reason) {
        status = WorkOrderStatus.CANCELLED;
        cancelledAt = now;
        cancelReason = reason;
    }
}
