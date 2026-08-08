package com.erp.manufacturing.module.sales.mapper;

import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.dto.PlanningDemandLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.repository.SalesOrderPlanningDemandProjection;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class SalesOrderMapper {

    public SalesOrderResponse toResponse(SalesOrder order, boolean includeLines) {
        List<SalesOrderLineResponse> lines = includeLines
                ? order.getLines().stream()
                .sorted(Comparator.comparing(SalesOrderLine::getLineNo))
                .map(this::toResponse)
                .toList()
                : List.of();
        return new SalesOrderResponse(
                order.getSalesOrderId(),
                order.getCompany().getCompanyId(),
                order.getCompany().getCode(),
                order.getPlant().getPlantId(),
                order.getPlant().getCode(),
                order.getOrderNo(),
                order.getCustomerName(),
                order.getOrderDate(),
                order.getStatus().name(),
                order.getNote(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion(),
                lines);
    }

    public SalesOrderLineResponse toResponse(SalesOrderLine line) {
        return new SalesOrderLineResponse(
                line.getSalesOrderLineId(),
                line.getLineNo(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                line.getItem().getUnit(),
                line.getOrderedQuantity(),
                line.getFulfilledQuantity(),
                line.openQuantity(),
                line.getDueDate());
    }

    /** {@code planningDemandId} comes from a separate batch lookup, not the projection — see
     *  {@code SalesOrderService.planningDemands}. */
    public PlanningDemandLineResponse toResponse(SalesOrderPlanningDemandProjection projection,
                                                 UUID planningDemandId) {
        return new PlanningDemandLineResponse(
                projection.getSalesOrderId(),
                projection.getSalesOrderCode(),
                projection.getSalesOrderLineId(),
                planningDemandId,
                projection.getLineNo(),
                projection.getItemId(),
                projection.getItemSku(),
                projection.getItemName(),
                projection.getUom(),
                projection.getOpenQuantity(),
                projection.getDueDate());
    }
}
