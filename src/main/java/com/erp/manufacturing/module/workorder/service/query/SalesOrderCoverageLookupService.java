package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.workorder.domain.WorkOrderDemandAllocation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.repository.WorkOrderDemandAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module read entry point (rule C7) for what work orders do for a sales order line (G6a):
 * the open coverage that feeds {@code workOrderCoveredQuantity}/{@code uncoveredQuantity}, and the
 * work orders listed in {@code linkedDocuments[]}. Both batch, never one query per line (C14).
 */
@Service
@RequiredArgsConstructor
public class SalesOrderCoverageLookupService {

    /** A cancelled or closed work order will never deliver the rest of its allocation. */
    static final Set<WorkOrderStatus> NOT_COVERING = Set.of(WorkOrderStatus.CANCELLED, WorkOrderStatus.CLOSED);

    private final WorkOrderDemandAllocationRepository allocationRepository;

    /** {@code salesOrderLineId} → open (not yet QC-released) work-order coverage; absent = none. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> sumOpenCoverageBySalesOrderLineIds(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds == null || salesOrderLineIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> coverage = new HashMap<>();
        for (WorkOrderDemandAllocationRepository.LineCoverageProjection row
                : allocationRepository.sumOpenCoverageBySalesOrderLineIds(salesOrderLineIds, NOT_COVERING)) {
            coverage.put(row.getSalesOrderLineId(), row.getQuantity());
        }
        return coverage;
    }

    /** Every work order allocated to the given lines, cancelled ones included (they are history). */
    @Transactional(readOnly = true)
    public List<LinkedWorkOrder> findLinkedWorkOrders(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds == null || salesOrderLineIds.isEmpty()) {
            return List.of();
        }
        return allocationRepository.findWithWorkOrderBySalesOrderLineIds(salesOrderLineIds).stream()
                .map(SalesOrderCoverageLookupService::toLinked)
                .toList();
    }

    private static LinkedWorkOrder toLinked(WorkOrderDemandAllocation allocation) {
        var workOrder = allocation.getWorkOrder();
        return new LinkedWorkOrder(
                allocation.getSalesOrderLineId(),
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                workOrder.getStatus().name(),
                workOrder.getProductItem().getItemId(),
                workOrder.getProductItem().getUnit(),
                allocation.getAllocatedQuantity(),
                allocation.getFulfilledQuantity());
    }

    /**
     * One allocation of a work order to a sales order line. {@code allocatedQuantity} is the part of
     * the work order that is for <em>this</em> line — never the work order's whole planned quantity.
     */
    public record LinkedWorkOrder(
            UUID salesOrderLineId,
            UUID workOrderId,
            String workOrderNo,
            String status,
            UUID productItemId,
            String uom,
            BigDecimal allocatedQuantity,
            BigDecimal fulfilledQuantity
    ) {}
}
