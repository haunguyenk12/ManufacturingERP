package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Material an execution actually used, per posted issue line (G8c, FE R13, D4). Trace only: the stock left the
 * warehouse when it was issued, so recording consumption posts nothing. Material cost stays where it has been since
 * P3 — accumulated when the material is issued ({@code WorkOrderCostAccumulator}); this table is not a second cost.
 */
@Entity
@Table(name = "production_execution_consumptions", indexes = {
        @Index(name = "idx_execution_consumptions_execution", columnList = "production_execution_id"),
        @Index(name = "idx_execution_consumptions_issue_line", columnList = "issue_line_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionExecutionConsumption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "consumption_id", updatable = false, nullable = false)
    private UUID consumptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_execution_id", nullable = false)
    private ProductionExecution productionExecution;

    @Column(name = "issue_line_id", nullable = false)
    private UUID issueLineId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;
}
