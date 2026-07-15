package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.VarianceStatus;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.repository.ComponentQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.WipTransactionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
                new WorkOrderWipSummaryResponse(scrapQuantity, reworkQuantity));
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

