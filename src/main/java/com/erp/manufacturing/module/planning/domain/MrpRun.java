package com.erp.manufacturing.module.planning.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
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

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public void start(Instant now) {
        status = MrpRunStatus.RUNNING;
        startedAt = now;
        errorMessage = null;
    }

    public void complete(Instant now, int demandCount, int requirementCount, int suggestionCount) {
        status = MrpRunStatus.COMPLETED;
        completedAt = now;
        totalDemandLines = demandCount;
        totalRequirementLines = requirementCount;
        totalSuggestionLines = suggestionCount;
    }

    public void fail(Instant now, String message) {
        status = MrpRunStatus.FAILED;
        completedAt = now;
        errorMessage = message;
    }
}
