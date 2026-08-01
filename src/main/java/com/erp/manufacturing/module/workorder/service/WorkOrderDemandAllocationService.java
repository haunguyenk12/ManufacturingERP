package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.sales.service.SalesOrderAllocationTarget;
import com.erp.manufacturing.module.sales.service.SalesOrderFulfillmentService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderDemandAllocation;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderDemandAllocationResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderDemandAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the link between a work order and the sales order lines it was raised for (spec §2.4, §7.1).
 *
 * <p>Not {@code @PreAuthorize}d: every entry point is reached from a work-order service method that
 * has already been authorized on this work order. Fulfilment is a <em>consequence</em> of
 * {@code PERM_QUALITY_DISPOSITION} rather than an action of its own, so there is no permission to
 * check here (NEXT_PHASE_PLAN F6 §1.4).
 */
@Service
@RequiredArgsConstructor
public class WorkOrderDemandAllocationService {

    private final WorkOrderDemandAllocationRepository allocationRepository;
    private final SalesOrderFulfillmentService salesOrderFulfillmentService;

    /**
     * Earmarks a freshly converted work order for the sales order line its demand came from.
     *
     * <p>A {@code null} line id is the normal case for {@code MANUAL}/{@code FORECAST} demand and is
     * not an error — those work orders simply have no allocation. The same silence covers a line id
     * that no longer resolves: refusing to create the work order would punish the planner for stale
     * planning data that the work order itself does not depend on.
     *
     * <p>The allocation is capped at the line's open quantity, because MRP lot-sizing may propose
     * more than the customer is still waiting for.
     */
    @Transactional
    public void allocate(WorkOrder workOrder, UUID salesOrderLineId) {
        if (salesOrderLineId == null) {
            return;
        }
        SalesOrderAllocationTarget target = salesOrderFulfillmentService
                .findAllocationTargets(List.of(salesOrderLineId))
                .get(salesOrderLineId);
        if (target == null) {
            return;
        }
        BigDecimal allocatedQuantity = workOrder.getPlannedQuantity().min(target.openQuantity());
        if (allocatedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        allocationRepository.save(WorkOrderDemandAllocation.builder()
                .workOrder(workOrder)
                .salesOrderLineId(salesOrderLineId)
                .allocatedQuantity(allocatedQuantity)
                .fulfilledQuantity(BigDecimal.ZERO)
                .build());
        salesOrderFulfillmentService.markInProduction(List.of(salesOrderLineId));
    }

    /**
     * Spreads quantity that QC has just released to {@code AVAILABLE} over this work order's
     * allocations, earliest due date first (spec §7.1).
     *
     * <p>Quantity left over once every allocation is covered is deliberately dropped — it is
     * unallocated finished goods sitting in the warehouse, not a failure. Likewise a work order with
     * no allocation at all (manually created, or planned from {@code MANUAL} demand) just returns.
     */
    @Transactional
    public void fulfill(WorkOrder workOrder, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<WorkOrderDemandAllocation> allocations = allocationRepository
                .findByWorkOrderWorkOrderIdIn(List.of(workOrder.getWorkOrderId()));
        if (allocations.isEmpty()) {
            return;
        }
        Map<UUID, SalesOrderAllocationTarget> targets = targetsOf(allocations);

        BigDecimal remaining = quantity;
        Map<UUID, BigDecimal> appliedByLine = new LinkedHashMap<>();
        List<WorkOrderDemandAllocation> touched = new ArrayList<>();
        for (WorkOrderDemandAllocation allocation : sortByDueDate(allocations, targets)) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            SalesOrderAllocationTarget target = targets.get(allocation.getSalesOrderLineId());
            // Two independent ceilings: what this allocation still claims, and what the sales order
            // line is still short of. Another work order may already have covered the line.
            BigDecimal room = allocation.remainingQuantity().min(target.openQuantity());
            if (room.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal applied = room.min(remaining);
            allocation.addFulfilled(applied);
            remaining = remaining.subtract(applied);
            appliedByLine.put(allocation.getSalesOrderLineId(), applied);
            touched.add(allocation);
        }

        if (appliedByLine.isEmpty()) {
            return;
        }
        allocationRepository.saveAll(touched);
        salesOrderFulfillmentService.applyFulfillment(appliedByLine);
    }

    /**
     * Allocations of a batch of work orders, grouped by work order. Two queries for a whole page —
     * one here, one inside {@link SalesOrderFulfillmentService#findAllocationTargets} (rule
     * {@code C14}).
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<WorkOrderDemandAllocationResponse>> findByWorkOrderIds(Collection<UUID> workOrderIds) {
        if (workOrderIds == null || workOrderIds.isEmpty()) {
            return Map.of();
        }
        List<WorkOrderDemandAllocation> allocations =
                allocationRepository.findByWorkOrderWorkOrderIdIn(workOrderIds);
        if (allocations.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SalesOrderAllocationTarget> targets = targetsOf(allocations);

        Map<UUID, List<WorkOrderDemandAllocationResponse>> byWorkOrder = new LinkedHashMap<>();
        for (WorkOrderDemandAllocation allocation : sortByDueDate(allocations, targets)) {
            SalesOrderAllocationTarget target = targets.get(allocation.getSalesOrderLineId());
            byWorkOrder.computeIfAbsent(allocation.getWorkOrder().getWorkOrderId(), id -> new ArrayList<>())
                    .add(new WorkOrderDemandAllocationResponse(
                            allocation.getAllocationId(),
                            allocation.getSalesOrderLineId(),
                            target.salesOrderCode(),
                            allocation.getAllocatedQuantity(),
                            allocation.getFulfilledQuantity(),
                            target.uom(),
                            target.dueDate()));
        }
        return byWorkOrder;
    }

    private Map<UUID, SalesOrderAllocationTarget> targetsOf(List<WorkOrderDemandAllocation> allocations) {
        return salesOrderFulfillmentService.findAllocationTargets(allocations.stream()
                .map(WorkOrderDemandAllocation::getSalesOrderLineId)
                .distinct()
                .toList());
    }

    /**
     * Earliest due date first, then line number, then line id. The last key is not decoration: two
     * lines of different orders can share a due date and a line number, and fulfilment must not
     * depend on the order the database happened to return rows in.
     *
     * <p>Allocations whose line no longer resolves are dropped rather than sorted last — every
     * caller dereferences the target immediately afterwards.
     */
    private List<WorkOrderDemandAllocation> sortByDueDate(List<WorkOrderDemandAllocation> allocations,
                                                          Map<UUID, SalesOrderAllocationTarget> targets) {
        return allocations.stream()
                .filter(allocation -> targets.containsKey(allocation.getSalesOrderLineId()))
                .sorted(Comparator
                        .<WorkOrderDemandAllocation, java.time.LocalDate>comparing(
                                allocation -> targets.get(allocation.getSalesOrderLineId()).dueDate())
                        .thenComparing(allocation -> targets.get(allocation.getSalesOrderLineId()).lineNo())
                        .thenComparing(WorkOrderDemandAllocation::getSalesOrderLineId))
                .toList();
    }
}
