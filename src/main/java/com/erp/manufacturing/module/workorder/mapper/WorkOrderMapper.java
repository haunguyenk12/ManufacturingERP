package com.erp.manufacturing.module.workorder.mapper;

import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderComponentLineResponse;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class WorkOrderMapper {

    public WorkOrderResponse toResponse(WorkOrder workOrder) {
        List<WorkOrderComponentLineResponse> lines = workOrder.getComponentLines() == null
                ? List.of()
                : workOrder.getComponentLines().stream()
                .sorted(Comparator.comparing(WorkOrderComponentLine::getLineNo))
                .map(this::toResponse)
                .toList();

        return new WorkOrderResponse(
                workOrder.getWorkOrderId(),
                workOrder.getCompany().getCompanyId(),
                workOrder.getPlant().getPlantId(),
                workOrder.getPlant().getCode(),
                workOrder.getWorkOrderNo(),
                workOrder.getProductItem().getItemId(),
                workOrder.getProductItem().getCode(),
                workOrder.getProductItem().getName(),
                workOrder.getBom().getBomId(),
                workOrder.getBomRevision(),
                workOrder.getOutputWarehouse().getWarehouseId(),
                workOrder.getOutputWarehouse().getCode(),
                workOrder.getPlannedQuantity(),
                workOrder.getCompletedQuantity(),
                workOrder.getPlannedQuantity().subtract(workOrder.getCompletedQuantity()),
                workOrder.getStatus().name(),
                workOrder.getPlannedStartAt(),
                workOrder.getPlannedEndAt(),
                workOrder.getReleasedAt(),
                workOrder.getCompletedAt(),
                workOrder.getCancelledAt(),
                workOrder.getNotes(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt(),
                lines);
    }

    public WorkOrderComponentLineResponse toResponse(WorkOrderComponentLine line) {
        return new WorkOrderComponentLineResponse(
                line.getComponentLineId(),
                line.getBomLine().getLineId(),
                line.getLineNo(),
                line.getComponentItem().getItemId(),
                line.getComponentItem().getCode(),
                line.getComponentItem().getName(),
                line.getQuantityPer(),
                line.getScrapRate(),
                line.getRequiredQuantity(),
                line.getIssuedQuantity(),
                line.remainingQuantity());
    }
}
