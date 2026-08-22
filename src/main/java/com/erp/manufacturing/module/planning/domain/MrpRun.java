package com.erp.manufacturing.module.planning.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "mrp_runs", indexes = {
        @Index(name = "idx_mrp_runs_company_plant_status_created", columnList = "company_id, plant_id, status, created_at"),
        @Index(name = "idx_mrp_runs_warehouse_id", columnList = "warehouse_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MrpRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mrp_run_id", updatable = false, nullable = false)
    private UUID mrpRunId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "horizon_start_date", nullable = false)
    private LocalDate horizonStartDate;

    @Column(name = "horizon_end_date", nullable = false)
    private LocalDate horizonEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MrpRunStatus status = MrpRunStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_demand_lines", nullable = false)
    @Builder.Default
    private Integer totalDemandLines = 0;

    @Column(name = "total_requirement_lines", nullable = false)
    @Builder.Default
    private Integer totalRequirementLines = 0;

    @Column(name = "total_suggestion_lines", nullable = false)
    @Builder.Default
    private Integer totalSuggestionLines = 0;

    /**
     * Total gross demand this run started from (spec §2.4 Run header, V39) — the sum of
     * {@code grossRequiredQuantity} across the <em>level-0</em> requirements only. Deeper levels are
     * derived from those, so summing every level would count the same demand once per BOM level.
     *
     * <p>Like the four counters below, this is a cache of a number the run transaction already
     * produced (D4 precedent); it is never recomputed on read. Runs from before F8 carry 0.
     */
    @Column(name = "gross_demand_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal grossDemandQuantity = BigDecimal.ZERO;

    @Column(name = "shortage_lines", nullable = false)
    @Builder.Default
    private Integer shortageLines = 0;

    @Column(name = "planned_work_orders", nullable = false)
    @Builder.Default
    private Integer plannedWorkOrders = 0;

    @Column(name = "planned_purchase_recommendations", nullable = false)
    @Builder.Default
    private Integer plannedPurchaseRecommendations = 0;

    @Column(name = "blocked_proposals", nullable = false)
    @Builder.Default
    private Integer blockedProposals = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Client-supplied {@code Idempotency-Key} (V58), {@code null} when the header was absent — the
     * header is optional and every run created before V58 has none. {@code uk_mrp_runs_idempotency_key}
     * is what actually stops a duplicate run; the replay lookup only decides whether to answer with
     * the existing one.
     */
    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    /** SHA-256 of the run request, so the same key replayed with a different body is a 409, not a silent replay. */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    /**
     * Derives the human-facing run code (spec §2.4) from the identifier.
     *
     * <p>Must run as {@code @PrePersist} and not after {@code save()}: Hibernate snapshots the
     * entity when it queues the insert, so a field set afterwards is simply absent from the INSERT
     * and the NOT NULL column blows up. {@code GenerationType.UUID} is a before-execution generator,
     * so {@code mrpRunId} is already assigned by the time this callback fires.
     *
     * <p>Must stay the same formula as the {@code V36} backfill
     * ({@code 'RUN-' || upper(substring(mrp_run_id::text, 1, 8))}).
     */
    @PrePersist
    public void assignCode() {
        if (code == null) {
            code = "RUN-" + mrpRunId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
    }

    public void start(Instant now) {
        status = MrpRunStatus.RUNNING;
        startedAt = now;
        errorMessage = null;
    }

    public void complete(Instant now,
                         int demandCount,
                         int requirementCount,
                         int suggestionCount,
                         BigDecimal grossDemand,
                         int shortageLineCount,
                         int plannedWorkOrderCount,
                         int plannedPurchaseRecommendationCount,
                         int blockedProposalCount) {
        status = MrpRunStatus.COMPLETED;
        completedAt = now;
        totalDemandLines = demandCount;
        totalRequirementLines = requirementCount;
        totalSuggestionLines = suggestionCount;
        grossDemandQuantity = grossDemand;
        shortageLines = shortageLineCount;
        plannedWorkOrders = plannedWorkOrderCount;
        plannedPurchaseRecommendations = plannedPurchaseRecommendationCount;
        blockedProposals = blockedProposalCount;
    }

    public void fail(Instant now, String message) {
        status = MrpRunStatus.FAILED;
        completedAt = now;
        errorMessage = message;
    }
}
