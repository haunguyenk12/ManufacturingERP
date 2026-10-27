package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * G11b: one calculation of a work order's production cost (FE C7). A {@code PREVIEW} run changes nothing but itself
 * (FE R16); the finalize-and-close command turns exactly one run into {@code FINALIZED}, re-calculated on the server
 * with the snapshot at close (FE R21, R26). {@code runVersion} is the run's own wire version (FE Q32): 1 as a preview,
 * 2 once finalized — {@code @Version} stays the optimistic lock.
 */
@Entity
@Table(name = "production_cost_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCostRun extends BaseEntity {

    public static final String PREVIEW = "PREVIEW";
    public static final String FINALIZED = "FINALIZED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "run_id", updatable = false, nullable = false)
    private UUID runId;

    @Column(name = "run_no", nullable = false, updatable = false, length = 40)
    private String runNo;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "plant_id", nullable = false, updatable = false)
    private UUID plantId;

    @Column(name = "state", nullable = false, length = 20)
    @Builder.Default
    private String state = PREVIEW;

    @Column(name = "run_version", nullable = false)
    @Builder.Default
    private Integer runVersion = 1;

    @Column(name = "source_version", nullable = false, length = 80)
    private String sourceVersion;

    @Column(name = "work_order_version", nullable = false)
    private Long workOrderVersion;

    @Column(name = "planned_total", precision = 19, scale = 6)
    private BigDecimal plannedTotal;

    @Column(name = "actual_total", precision = 19, scale = 6)
    private BigDecimal actualTotal;

    /** The full {@code CostSnapshot} as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "finalized_by")
    private UUID finalizedBy;

    @Column(name = "close_reason", columnDefinition = "TEXT")
    private String closeReason;

    public boolean isFinalized() {
        return FINALIZED.equals(state);
    }
}
