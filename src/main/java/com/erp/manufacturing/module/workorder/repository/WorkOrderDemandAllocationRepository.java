package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WorkOrderDemandAllocation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkOrderDemandAllocationRepository extends JpaRepository<WorkOrderDemandAllocation, UUID> {

    /**
     * Every allocation of a batch of work orders in one query. A page of work orders resolves its
     * {@code allocations[]} through this, never one call per row (rule {@code C14}).
     */
    List<WorkOrderDemandAllocation> findByWorkOrderWorkOrderIdIn(Collection<UUID> workOrderIds);

    interface LineCoverageProjection {
        UUID getSalesOrderLineId();
        BigDecimal getQuantity();
    }

    /**
     * Work-order coverage of each sales order line that QC has not released yet (G6a): the sum of
     * {@code allocated − fulfilled} over allocations whose work order is still going to deliver output.
     * {@code DRAFT} work orders count (FE Q2 — otherwise the planner converts the same demand twice);
     * work orders in {@code excludedStatuses} ({@code CANCELLED}, {@code CLOSED}) never deliver the rest.
     * The QC-released part is reported separately as {@code qcAcceptedQuantity}, so the two never
     * overlap.
     */
    @Query("""
            select a.salesOrderLineId as salesOrderLineId,
                   coalesce(sum(a.allocatedQuantity - a.fulfilledQuantity), 0) as quantity
            from WorkOrderDemandAllocation a
            join a.workOrder w
            where a.salesOrderLineId in :salesOrderLineIds
              and w.status not in :excludedStatuses
            group by a.salesOrderLineId
            """)
    List<LineCoverageProjection> sumOpenCoverageBySalesOrderLineIds(
            @Param("salesOrderLineIds") Collection<UUID> salesOrderLineIds,
            @Param("excludedStatuses") Collection<WorkOrderStatus> excludedStatuses);

    /** Allocations of the given lines with their work order and product, for {@code linkedDocuments[]}. */
    @Query("""
            select a
            from WorkOrderDemandAllocation a
            join fetch a.workOrder w
            join fetch w.productItem p
            where a.salesOrderLineId in :salesOrderLineIds
            order by w.workOrderNo asc, a.allocationId asc
            """)
    List<WorkOrderDemandAllocation> findWithWorkOrderBySalesOrderLineIds(
            @Param("salesOrderLineIds") Collection<UUID> salesOrderLineIds);
}
