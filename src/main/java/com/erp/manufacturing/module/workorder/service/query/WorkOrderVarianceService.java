package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.costing.service.ItemStandardCostLookupService;
import com.erp.manufacturing.module.costing.service.StandardCostBreakdown;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.VarianceStatus;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCostAccumulator;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.repository.ComponentQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.WipTransactionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderCostAccumulatorRepository;
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
    private final WorkOrderCostAccumulatorRepository workOrderCostAccumulatorRepository;
    private final ItemStandardCostLookupService itemStandardCostLookupService;

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

        UUID companyId = workOrder.getCompany().getCompanyId();
        List<WorkOrderMaterialVarianceLineResponse> materialLines = workOrder.getComponentLines().stream()
                .map(line -> toVarianceLine(companyId, line,
                        issuedByLine.getOrDefault(line.getComponentLineId(), BigDecimal.ZERO)))
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
                toTimeVariance(workOrder),
                toCostVariance(workOrder));
    }

    /**
     * {@code standard*} = {@code CostingService.calculateStandardCost} of the product item times
     * {@code plannedQuantity} (what it should have cost). {@code actual*} reads the work order's
     * {@link WorkOrderCostAccumulator} — all-zero when the row does not exist yet, which is exactly
     * a work order that has issued nothing and reported nothing.
     */
    private WorkOrderCostVarianceResponse toCostVariance(WorkOrder workOrder) {
        StandardCostBreakdown breakdown = itemStandardCostLookupService.findStandardCostBreakdown(
                workOrder.getCompany().getCompanyId(), workOrder.getProductItem().getItemId());
        BigDecimal plannedQuantity = workOrder.getPlannedQuantity();
        BigDecimal standardMaterialCost = breakdown.materialCost().multiply(plannedQuantity);
        BigDecimal standardLaborCost = breakdown.laborCost().multiply(plannedQuantity);
        BigDecimal standardOverheadCost = breakdown.overheadCost().multiply(plannedQuantity);
        BigDecimal standardTotalCost = standardMaterialCost.add(standardLaborCost).add(standardOverheadCost);

        WorkOrderCostAccumulator accumulator = workOrderCostAccumulatorRepository
                .findByWorkOrderWorkOrderId(workOrder.getWorkOrderId())
                .orElse(null);
        BigDecimal actualMaterialCost = accumulator != null ? accumulator.getMaterialCostAccumulated() : BigDecimal.ZERO;
        BigDecimal actualLaborCost = accumulator != null ? accumulator.getLaborCostAccumulated() : BigDecimal.ZERO;
        BigDecimal actualOverheadCost = accumulator != null ? accumulator.getOverheadCostAccumulated() : BigDecimal.ZERO;
        BigDecimal actualTotalCost = actualMaterialCost.add(actualLaborCost).add(actualOverheadCost);

        return new WorkOrderCostVarianceResponse(
                standardMaterialCost, standardLaborCost, standardOverheadCost, standardTotalCost,
                actualMaterialCost, actualLaborCost, actualOverheadCost, actualTotalCost,
                actualTotalCost.subtract(standardTotalCost));
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

    private WorkOrderMaterialVarianceLineResponse toVarianceLine(UUID companyId,
                                                                 WorkOrderComponentLine line,
                                                                 BigDecimal actualQuantity) {
        BigDecimal variance = actualQuantity.subtract(line.getRequiredQuantity());
        BigDecimal standardUnitCost = itemStandardCostLookupService.findStandardUnitCost(
                companyId, line.getComponentItem().getItemId());
        return new WorkOrderMaterialVarianceLineResponse(
                line.getComponentLineId(),
                line.getComponentItem().getItemId(),
                line.getComponentItem().getCode(),
                line.getComponentItem().getName(),
                line.getRequiredQuantity(),
                actualQuantity,
                variance,
                varianceStatus(variance).name(),
                variance.multiply(standardUnitCost));
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

