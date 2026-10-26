package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.SalesOrderCoverageLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the sales order detail with the G6a line figures — three batch lookups for the whole order,
 * none per line (rule C14). Shared by {@link SalesOrderService} and
 * {@link SalesOrderReservationService} so a hold and a plain read report the same numbers.
 */
@Component
@RequiredArgsConstructor
public class SalesOrderDetailAssembler {

    private final SalesOrderStockReservationService stockReservationService;
    private final SalesOrderCoverageLookupService coverageLookupService;
    private final SalesOrderMapper mapper;
    private final SalesPriceVisibility priceVisibility;
    private final DeliveryLookupService deliveryLookupService;

    public SalesOrderResponse toDetail(SalesOrder order) {
        List<UUID> lineIds = lineIds(order);
        return mapper.toDetailResponse(order,
                stockReservationService.sumActiveHeldBySalesOrderLineIds(lineIds),
                coverageLookupService.sumOpenCoverageBySalesOrderLineIds(lineIds),
                coverageLookupService.findLinkedWorkOrders(lineIds),
                priceVisibility.canSeePrices(order.getPlant().getPlantId()),
                deliveryLookupService.sumDeliveredBySalesOrderLineIds(lineIds));
    }

    /** {@code max(0, ordered − max(qcAccepted, delivered) − held − workOrderCovered)} of one line, read now (D8). */
    public BigDecimal uncoveredQuantity(SalesOrderLine line) {
        List<UUID> ids = List.of(line.getSalesOrderLineId());
        Map<UUID, BigDecimal> held = stockReservationService.sumActiveHeldBySalesOrderLineIds(ids);
        Map<UUID, BigDecimal> coverage = coverageLookupService.sumOpenCoverageBySalesOrderLineIds(ids);
        Map<UUID, BigDecimal> delivered = deliveryLookupService.sumDeliveredBySalesOrderLineIds(ids);
        return SalesOrderMapper.uncovered(
                line.getOrderedQuantity(),
                line.getFulfilledQuantity(),
                delivered.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO),
                held.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO),
                coverage.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO));
    }

    private static List<UUID> lineIds(SalesOrder order) {
        return order.getLines().stream()
                .map(SalesOrderLine::getSalesOrderLineId)
                .filter(Objects::nonNull)
                .toList();
    }
}
