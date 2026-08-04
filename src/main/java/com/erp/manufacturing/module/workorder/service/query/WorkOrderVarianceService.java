package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.VarianceStatus;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.repository.ComponentQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.WipTransactionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderVarianceService {

    private final MaterialIssueLineRepository materialIssueLineRepository;
    private final ProductionReceiptLineRepository productionReceiptLineRepository;
    private final WipTransactionRepository wipTransactionRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ProductionExecutionRepository productionExecutionRepository;
    private final WorkOrderOperationRepository workOrderOperationRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_VARIANCE_READ', #workOrderId)")
    public WorkOrderVarianceResponse getVariance(UUID workOrderId) {
        WorkOrder workOrder = workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId));
        Map<UUID, BigDecimal> issuedByLine = materialIssueLineRepository.sumIssuedByWorkOrder(workOrderId).stream()
                .collect(Collectors.toMap(
                        ComponentQuantityProjection::getComponentLineId,
                        ComponentQuantityProjection::getQuantity));
        BigDecimal actualOutput = productionReceiptLineRepository.sumReceivedQuantityByWorkOrder(workOrderId);
        BigDecimal scrapQuantity = wipTransactionRepository.sumQuantityByWorkOrderAndType(
                workOrderId, WipTransactionType.SCRAP_REPORTED);
        BigDecimal reworkQuantity = wipTransactionRepository.sumQuantityByWorkOrderAndType(
                workOrderId, WipTransactionType.REWORK_REPORTED);

        List<WorkOrderMaterialVarianceLineResponse> materialLines = workOrder.getComponentLines().stream()
                .map(line -> toVarianceLine(line, issuedByLine.getOrDefault(line.getComponentLineId(), BigDecimal.ZERO)))
                .toList();

        return new WorkOrderVarianceResponse(
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                workOrder.getProductItem().getItemId(),
                workOrder.getProductItem().getCode(),
                workOrder.getStatus().name(),
                materialLines,
                new WorkOrderOutputVarianceResponse(
                        workOrder.getPlannedQuantity(),
                        actualOutput,
                        actualOutput.subtract(workOrder.getPlannedQuantity())),
                new WorkOrderWipSummaryResponse(scrapQuantity, reworkQuantity),
                toTimeVariance(workOrder));
    }

    /**
     * C2-4: computed in Java, not JPQL — {@code Duration.between} on {@code Instant} does not port
     * cleanly to JPQL, and a single work order's operation/execution counts are small (same shape as
     * the {@code materialLines} loop above, not the per-row-query pattern rule {@code C14} forbids).
     *
     * <p>Operations are read via {@link WorkOrderOperationRepository}, not
     * {@code workOrder.getOperations()}: {@code WorkOrder} already join-fetches {@code componentLines}
     * in {@link WorkOrderRepository#findWithDetailsByWorkOrderId}, and Hibernate cannot join-fetch two
     * {@code List} ("bag") collections in one query ({@code MultipleBagFetchException}). One extra
     * query, not one per row, keeps this within rule {@code C14}.
     */
    private WorkOrderTimeVarianceResponse toTimeVariance(WorkOrder workOrder) {
        List<WorkOrderOperation> operations = workOrderOperationRepository
                .findByWorkOrderWorkOrderIdOrderBySequenceAsc(workOrder.getWorkOrderId());
        BigDecimal plannedMinutes = operations.stream()
                .map(operation -> plannedMinutesFor(operation, workOrder.getPlannedQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ProductionExecution> executions = productionExecutionRepository
                .findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrder.getWorkOrderId());
        BigDecimal actualMinutes = executions.stream()
                .filter(execution -> execution.getActualStartedAt() != null && execution.getActualEndedAt() != null)
                .map(execution -> BigDecimal.valueOf(
                        Duration.between(execution.getActualStartedAt(), execution.getActualEndedAt()).toMinutes()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new WorkOrderTimeVarianceResponse(plannedMinutes, actualMinutes, actualMinutes.subtract(plannedMinutes));
    }

    private BigDecimal plannedMinutesFor(WorkOrderOperation operation, BigDecimal plannedQuantity) {
        return operation.getSetupMinutes().add(operation.getRunMinutesPerUnit().multiply(plannedQuantity));
    }

    private WorkOrderMaterialVarianceLineResponse toVarianceLine(WorkOrderComponentLine line,
                                                                 BigDecimal actualQuantity) {
        BigDecimal variance = actualQuantity.subtract(line.getRequiredQuantity());
        return new WorkOrderMaterialVarianceLineResponse(
                line.getComponentLineId(),
                line.getComponentItem().getItemId(),
                line.getComponentItem().getCode(),
                line.getComponentItem().getName(),
                line.getRequiredQuantity(),
                actualQuantity,
                variance,
                varianceStatus(variance).name());
    }

    private VarianceStatus varianceStatus(BigDecimal variance) {
        int comparison = variance.compareTo(BigDecimal.ZERO);
        if (comparison < 0) {
            return VarianceStatus.UNDER_ISSUED;
        }
        if (comparison > 0) {
            return VarianceStatus.OVER_ISSUED;
        }
        return VarianceStatus.MATCHED;
    }
}

