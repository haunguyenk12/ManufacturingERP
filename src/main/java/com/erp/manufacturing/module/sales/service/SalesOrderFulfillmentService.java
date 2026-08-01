package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The entry point {@code workorder} uses to read and advance sales order lines it holds an
 * allocation against (rule {@code C7}) — sales never learns about work orders in return, and
 * {@code workorder} never touches {@code SalesOrderLineRepository}.
 *
 * <p>{@code NEXT_PHASE_PLAN.md} called this a {@code SalesOrderLookupService}. It writes as well as
 * reads (that is the whole point of fulfilment), so naming it "lookup" would have made it the one
 * service in the repo whose name lies about what it does.
 *
 * <p>No {@code @PreAuthorize}: the caller
 * ({@code ProductionReceiptService.qcDisposition} / {@code WorkOrderService.createFromMrp}) has
 * already been authorized on its own work order, and fulfilment is a consequence of
 * {@code PERM_QUALITY_DISPOSITION}, not a separate sales action a QC user should have to hold.
 * Same shape as {@code PlanningDemandService.createFromSalesOrderLine}.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderFulfillmentService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;

    /**
     * Flat view of the given lines, keyed by line id. One query regardless of how many lines are
     * asked for (rule {@code C14}); ids that no longer resolve are simply absent from the map.
     */
    @Transactional(readOnly = true)
    public Map<UUID, SalesOrderAllocationTarget> findAllocationTargets(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds == null || salesOrderLineIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SalesOrderAllocationTarget> targets = new LinkedHashMap<>();
        for (SalesOrderAllocationTarget target : salesOrderLineRepository.findAllocationTargets(salesOrderLineIds)) {
            targets.put(target.salesOrderLineId(), target);
        }
        return targets;
    }

    /**
     * Marks the orders behind these lines as {@code IN_PRODUCTION} — spec §7: an order is in
     * production as soon as one work order has been allocated to it.
     */
    @Transactional
    public void markInProduction(Collection<UUID> salesOrderLineIds) {
        List<SalesOrder> orders = findOrdersOfLines(salesOrderLineIds);
        if (orders.isEmpty()) {
            return;
        }
        orders.forEach(SalesOrder::markInProduction);
        salesOrderRepository.saveAll(orders);
    }

    /**
     * Adds the quantity QC has just released to each line and rolls the order status up (spec §7.1).
     * Quantity beyond {@code orderedQuantity} is clipped by {@link SalesOrderLine#addFulfilled}
     * rather than rejected: surplus output is free stock, not a failed QC.
     */
    @Transactional
    public void applyFulfillment(Map<UUID, BigDecimal> quantityBySalesOrderLineId) {
        if (quantityBySalesOrderLineId == null || quantityBySalesOrderLineId.isEmpty()) {
            return;
        }
        List<SalesOrder> orders = findOrdersOfLines(quantityBySalesOrderLineId.keySet());
        for (SalesOrder order : orders) {
            for (SalesOrderLine line : order.getLines()) {
                BigDecimal quantity = quantityBySalesOrderLineId.get(line.getSalesOrderLineId());
                if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                    line.addFulfilled(quantity);
                }
            }
            rollUpStatus(order);
        }
        salesOrderRepository.saveAll(orders);
    }

    /**
     * {@code FULFILLED} once every line is covered, {@code PARTIALLY_FULFILLED} once any line has
     * moved. An order with nothing fulfilled yet keeps whatever status it had — that case belongs
     * to {@link #markInProduction}.
     */
    private void rollUpStatus(SalesOrder order) {
        if (order.getStatus() == SalesOrderStatus.CANCELLED || order.getLines().isEmpty()) {
            return;
        }
        if (order.getLines().stream().allMatch(SalesOrderLine::isFullyFulfilled)) {
            order.markFulfilled();
        } else if (order.getLines().stream()
                .anyMatch(line -> line.getFulfilledQuantity().compareTo(BigDecimal.ZERO) > 0)) {
            order.markPartiallyFulfilled();
        }
    }

    /**
     * Two batch queries: line ids to order ids, then the orders with <b>all</b> their lines. The
     * roll-up needs the untouched lines too, which a single filtering fetch-join would drop.
     */
    private List<SalesOrder> findOrdersOfLines(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds == null || salesOrderLineIds.isEmpty()) {
            return List.of();
        }
        List<UUID> orderIds = salesOrderLineRepository.findOrderIdsByLineIds(salesOrderLineIds);
        return orderIds.isEmpty() ? List.of() : salesOrderRepository.findBySalesOrderIdIn(orderIds);
    }
}
