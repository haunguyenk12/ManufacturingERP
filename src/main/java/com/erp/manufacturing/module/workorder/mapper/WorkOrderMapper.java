package com.erp.manufacturing.module.workorder.mapper;

import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderComponentLineResponse;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderDemandAllocationResponse;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderOperationResponse;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WorkOrderMapper {

    /**
     * @param reservedByComponentLine remaining {@code ACTIVE} reservation per component line, resolved
     *        by the caller in one batch query for the whole page (rule C14). Lines absent from the map
     *        have nothing reserved and render as zero — the aggregate returns no row for them rather
     *        than a zero row.
     */
    public WorkOrderResponse toResponse(WorkOrder workOrder,
                                        List<WorkOrderDemandAllocationResponse> allocations,
                                        Map<UUID, BigDecimal> reservedByComponentLine) {
        List<WorkOrderComponentLineResponse> lines = workOrder.getComponentLines() == null
                ? List.of()
                : workOrder.getComponentLines().stream()
                .sorted(Comparator.comparing(WorkOrderComponentLine::getLineNo))
                .map(line -> toResponse(line, reservedByComponentLine))
                .toList();

        List<WorkOrderOperationResponse> operations = workOrder.getOperations() == null
                ? List.of()
                : workOrder.getOperations().stream()
                .sorted(Comparator.comparing(WorkOrderOperation::getSequence))
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
                workOrder.getProductItem().getUnit(),
                workOrder.getBom().getBomId(),
                workOrder.getBomRevision(),
                // The BOM is captured while the work order is being built, so the work order's own
                // createdAt IS the capture instant — no separate column (see WorkOrderResponse).
                workOrder.getCreatedAt(),
                workOrder.getSourceRoutingId(),
                workOrder.getSourceRoutingCode(),
                workOrder.getSourceRoutingVersion(),
                workOrder.getRoutingCapturedAt(),
                workOrder.getPlanningRunId(),
                workOrder.getPlanningRunCode(),
                workOrder.getPlanningProposalId(),
                workOrder.getOutputWarehouse().getWarehouseId(),
                workOrder.getOutputWarehouse().getCode(),
                workOrder.getPlannedQuantity(),
                workOrder.getCompletedQuantity(),
                workOrder.getPlannedQuantity().subtract(workOrder.getCompletedQuantity()),
                workOrder.getActualGoodQuantity(),
                workOrder.getActualScrapQuantity(),
                workOrder.getActualReworkQuantity(),
                workOrder.availableToReceipt(),
                workOrder.getStatus().name(),
                workOrder.getPlannedStartAt(),
                workOrder.getPlannedEndAt(),
                workOrder.getReleasedAt(),
                workOrder.getExecutionStartedAt(),
                // Spec §5.3 executionCompletedAt: the same instant as completedAt, because
                // WorkOrder.complete() is only ever called from shop-floor reporting (B53).
                workOrder.getCompletedAt(),
                workOrder.getCompletedAt(),
                workOrder.getCancelledAt(),
                workOrder.getCancelReason(),
                workOrder.getBlockedAt(),
                workOrder.getBlockReason(),
                workOrder.getNotes(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt(),
                lines,
                operations,
                allocations == null ? List.of() : allocations);
    }

    public WorkOrderOperationResponse toResponse(WorkOrderOperation operation) {
        return new WorkOrderOperationResponse(
                operation.getWorkOrderOperationId(),
                operation.getSourceRoutingOperationId(),
                operation.getSequence(),
                operation.getName(),
                operation.getWorkCenterCode(),
                operation.getWorkCenter() == null ? null : operation.getWorkCenter().getWorkCenterId(),
                operation.getSetupMinutes(),
                operation.getRunMinutesPerUnit(),
                operation.getPlannedStartAt(),
                operation.getPlannedEndAt(),
                operation.getVersion());
    }

    public WorkOrderComponentLineResponse toResponse(WorkOrderComponentLine line,
                                                     Map<UUID, BigDecimal> reservedByComponentLine) {
        return new WorkOrderComponentLineResponse(
                line.getComponentLineId(),
                line.getBomLine().getLineId(),
                line.getLineNo(),
                line.getComponentItem().getItemId(),
                line.getComponentItem().getCode(),
                line.getComponentItem().getName(),
                line.getComponentItem().getUnit(),
                line.getQuantityPer(),
                line.getScrapRate(),
                line.getRequiredQuantity(),
                // Absent from the map means nothing is reserved, not "unknown": a group-by returns no
                // row for such a line rather than a zero row.
                reservedByComponentLine.getOrDefault(line.getComponentLineId(), BigDecimal.ZERO),
                line.getIssuedQuantity(),
                line.remainingQuantity());
    }
}
