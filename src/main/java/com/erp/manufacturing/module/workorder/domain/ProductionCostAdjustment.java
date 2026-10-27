package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G11c: one value-only entry written at finalize-and-close (FE §3.6, E9). Quantity is always 0. A stock entry carries the
 * value still on hand ({@code stockValueDelta = allocatedAmount}); a delivery entry carries the cost of goods already
 * delivered ({@code stockValueDelta = 0}) and points at the delivery line and its original ISSUE, which is never
 * rewritten (FE R20). Immutable once written (invariant B262).
 */
@Entity
@Table(name = "production_cost_adjustments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCostAdjustment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "adjustment_id", updatable = false, nullable = false)
    private UUID adjustmentId;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "plant_id", nullable = false, updatable = false)
    private UUID plantId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;

    @Column(name = "line_no", nullable = false, updatable = false)
    private Integer lineNo;

    @Column(name = "receipt_id", nullable = false, updatable = false)
    private UUID receiptId;

    @Column(name = "decision_id", updatable = false)
    private UUID decisionId;

    @Column(name = "quality", nullable = false, updatable = false, length = 20)
    private String quality;

    @Column(name = "lot_id", updatable = false)
    private UUID lotId;

    @Column(name = "delivery_id", updatable = false)
    private UUID deliveryId;

    @Column(name = "delivery_line_id", updatable = false)
    private UUID deliveryLineId;

    @Column(name = "adjusted_movement_id", updatable = false)
    private UUID adjustedMovementId;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    /** The physical quantity this value covers: on hand (stock entry) or delivered (delivery entry). */
    @Column(name = "covered_quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal coveredQuantity;

    @Column(name = "stock_value_delta", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal stockValueDelta;

    @Column(name = "allocated_amount", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal allocatedAmount;

    /** {@code null}: the value of the pool was unknown before close — not 0 (FE §3.6). */
    @Column(name = "stock_value_before", updatable = false, precision = 19, scale = 6)
    private BigDecimal stockValueBefore;

    @Column(name = "stock_value_after", updatable = false, precision = 19, scale = 6)
    private BigDecimal stockValueAfter;

    @Column(name = "reason", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String reason;
}
