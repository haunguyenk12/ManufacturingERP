package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Earmarks part of a work order's output for one sales order line (spec §2.4). Created when an
 * approved MAKE proposal whose demand came from a sales order is converted, and consumed when QC
 * releases the output to {@code AVAILABLE} (spec §7.1).
 *
 * <p>{@code salesOrderLineId} is a plain column rather than a {@code @ManyToOne}: the sales module
 * already depends on planning, which depends on this module, so a typed association would close a
 * compile-time cycle. Everything this module needs about the line (due date, line no, open
 * quantity) is read through {@code SalesOrderFulfillmentService} instead — rule {@code C7}. The
 * foreign key still exists in the database.
 */
@Entity
@Table(name = "work_order_demand_allocations", indexes = {
        @Index(name = "idx_wo_demand_allocations_work_order", columnList = "work_order_id"),
        @Index(name = "idx_wo_demand_allocations_sales_order_line", columnList = "sales_order_line_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderDemandAllocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "allocation_id", updatable = false, nullable = false)
    private UUID allocationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(name = "sales_order_line_id", nullable = false)
    private UUID salesOrderLineId;

    @Column(name = "allocated_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal allocatedQuantity;

    @Column(name = "fulfilled_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal fulfilledQuantity = BigDecimal.ZERO;

    /** How much of this allocation QC has not released yet. */
    public BigDecimal remainingQuantity() {
        return allocatedQuantity.subtract(fulfilledQuantity);
    }

    public void addFulfilled(BigDecimal quantity) {
        fulfilledQuantity = fulfilledQuantity.add(quantity);
    }
}
