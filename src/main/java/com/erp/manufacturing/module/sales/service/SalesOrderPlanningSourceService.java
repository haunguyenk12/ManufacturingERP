package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.SalesOrderCoverageLookupService;
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
 * Entry point for {@code module/planning} into sales order lines (G6b, rule C7). No
 * {@code @PreAuthorize}: the planning caller has already been authorised on the plant, and the
 * lines it asks about are checked against that plant by the caller.
 *
 * <p>Four batch queries for any number of lines (C14): the lines, the active holds, the open work
 * order coverage and (G10b) the POSTED delivery quantity.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderPlanningSourceService {

    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderStockReservationService stockReservationService;
    private final SalesOrderCoverageLookupService coverageLookupService;
    private final DeliveryLookupService deliveryLookupService;

    /** Keyed by line id; a line that does not exist is simply absent. */
    @Transactional(readOnly = true)
    public Map<UUID, SalesOrderPlanningSource> findSources(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds.isEmpty()) {
            return Map.of();
        }
        List<SalesOrderLine> lines = salesOrderLineRepository.findWithOrderAndItemByLineIds(salesOrderLineIds);
        List<UUID> ids = lines.stream().map(SalesOrderLine::getSalesOrderLineId).toList();
        Map<UUID, BigDecimal> held = stockReservationService.sumActiveHeldBySalesOrderLineIds(ids);
        Map<UUID, BigDecimal> coverage = coverageLookupService.sumOpenCoverageBySalesOrderLineIds(ids);
        Map<UUID, BigDecimal> delivered = deliveryLookupService.sumDeliveredBySalesOrderLineIds(ids);
        Map<UUID, SalesOrderPlanningSource> result = new LinkedHashMap<>();
        for (SalesOrderLine line : lines) {
            SalesOrder order = line.getSalesOrder();
            BigDecimal lineHeld = held.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO);
            BigDecimal lineCoverage = coverage.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO);
            result.put(line.getSalesOrderLineId(), new SalesOrderPlanningSource(
                    line.getSalesOrderLineId(),
                    order.getSalesOrderId(),
                    order.getOrderNo(),
                    line.getLineNo(),
                    order.getCompany().getCompanyId(),
                    order.getPlant().getPlantId(),
                    order.getStatus().name(),
                    SalesOrderService.PLANNING_ELIGIBLE_STATUSES.contains(order.getStatus()),
                    line.getItem().getItemId(),
                    line.getItem().getUnit(),
                    line.getOrderedQuantity(),
                    line.getFulfilledQuantity(),
                    lineHeld,
                    lineCoverage,
                    SalesOrderMapper.uncovered(line.getOrderedQuantity(), line.getFulfilledQuantity(),
                            delivered.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO), lineHeld, lineCoverage),
                    line.getDueDate()));
        }
        return result;
    }
}
