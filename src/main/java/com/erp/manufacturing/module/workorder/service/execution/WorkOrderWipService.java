package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.operation.WorkOrderOperationProgressResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipMaterialLineResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipOutputResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipReworkLineResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipSection;
import com.erp.manufacturing.module.workorder.dto.wip.WipWorkOrderSummaryResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WorkOrderWipResponse;
import com.erp.manufacturing.module.workorder.repository.KeyedQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.OpenReceiptQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderReworkTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * G8d (FE R15, D8): the work in progress of work orders, read live from the ledgers that already exist — issue lines,
 * consumption, returns, the operation input ledger, executions and production receipts. Nothing is stored for it.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderWipService {

    /** Where WIP can exist when the list is not filtered by status: released and not yet closed or cancelled. */
    static final Set<WorkOrderStatus> DEFAULT_STATUSES =
            EnumSet.of(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED);

    private final WorkOrderRepository workOrderRepository;
    private final ProductionExecutionRepository executionRepository;
    private final ProductionReceiptRepository receiptRepository;
    private final OperationLedger operationLedger;
    private final MaterialSourceLedger sourceLedger;
    private final WorkOrderExecutionSupport support;

    /**
     * Plant WIP list. Without {@code status} it lists RELEASED, IN_PROGRESS and COMPLETED work orders; {@code search}
     * matches the work order number or product SKU; {@code fromDate}/{@code toDate} are plant-local days on the work
     * order's {@code createdAt}, both inclusive.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WIP_READ', 'PLANT', #plantId)")
    public PageResult<WipWorkOrderSummaryResponse> listByPlant(UUID plantId, WorkOrderStatus status, UUID workOrderId,
                                                               String search, LocalDate fromDate, LocalDate toDate,
                                                               Pageable pageable) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("toDate", "must not be before fromDate"));
        }
        ZoneId zone = (fromDate == null && toDate == null) ? null : support.plantZone(plantId);
        Page<WorkOrder> page = workOrderRepository.searchWip(plantId,
                status == null ? DEFAULT_STATUSES : EnumSet.of(status), workOrderId, support.trimToNull(search),
                fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant(),
                pageable);
        List<UUID> ids = page.getContent().stream().map(WorkOrder::getWorkOrderId).toList();
        Map<UUID, WorkOrderReworkTotals> rework = ids.isEmpty() ? Map.of()
                : executionRepository.sumReworkByWorkOrderIds(ids).stream()
                .collect(Collectors.toMap(WorkOrderReworkTotals::getWorkOrderId, Function.identity()));
        return PageResult.from(page.map(workOrder -> summary(workOrder, rework.get(workOrder.getWorkOrderId()))));
    }

    /** The WIP of one work order; {@code section} narrows it to one part, omitted means every part. */
    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WIP_READ', #workOrderId)")
    public WorkOrderWipResponse get(UUID workOrderId, WipSection section) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        boolean all = section == null;
        List<WipMaterialLineResponse> materials = all || section == WipSection.MATERIALS
                ? sourceLedger.lines(workOrder) : null;
        List<OperationLedger.OperationState> states = all || section == WipSection.OPERATIONS || section == WipSection.REWORK
                ? operationLedger.load(workOrderId) : List.of();
        List<WorkOrderOperationProgressResponse> operations = all || section == WipSection.OPERATIONS
                ? states.stream().map(state -> OperationLedger.toResponse(state, workOrder)).toList() : null;
        List<WipReworkLineResponse> rework = all || section == WipSection.REWORK ? rework(workOrderId) : null;
        WipOutputResponse output = all || section == WipSection.AWAITING_RECEIPT ? output(workOrder) : null;
        boolean known = workOrder.isOperationTracking();
        return new WorkOrderWipResponse(workOrder.getWorkOrderId(), workOrder.getWorkOrderNo(), workOrder.getStatus(),
                workOrder.getVersion(), known, known, section, materials, operations, rework, output);
    }

    /**
     * Executions whose rework is still waiting for a retry. Operations were loaded just before in the same transaction,
     * so reading each execution's operation hits the persistence context, not the database.
     */
    private List<WipReworkLineResponse> rework(UUID workOrderId) {
        List<ProductionExecution> withRework = executionRepository.findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrderId)
                .stream()
                .filter(execution -> execution.getReworkQuantity().signum() > 0)
                .toList();
        if (withRework.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> used = executionRepository.sumRetryProcessedBySource(withRework.stream()
                        .map(ProductionExecution::getProductionExecutionId).toList()).stream()
                .collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
        return withRework.stream()
                .map(execution -> {
                    BigDecimal retryUsed = used.getOrDefault(execution.getProductionExecutionId(), BigDecimal.ZERO);
                    BigDecimal pending = execution.getReworkQuantity().subtract(retryUsed);
                    return pending.signum() > 0 ? new WipReworkLineResponse(
                            execution.getProductionExecutionId(),
                            execution.getCode(),
                            execution.getOperation() == null ? null : execution.getOperation().getWorkOrderOperationId(),
                            execution.getOperation() == null ? null : execution.getOperation().getSequence(),
                            execution.getOperation() == null ? null : execution.getOperation().getName(),
                            execution.getReworkQuantity(),
                            retryUsed,
                            pending,
                            execution.getActualEndedAt()) : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private WipOutputResponse output(WorkOrder workOrder) {
        boolean known = workOrder.isOperationTracking();
        BigDecimal open = receiptRepository.sumOpenQuantityByWorkOrderIds(List.of(workOrder.getWorkOrderId())).stream()
                .map(OpenReceiptQuantityProjection::getOpenQuantity)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        return new WipOutputResponse(
                workOrder.getProductItem().getUnit(),
                workOrder.getPlannedQuantity(),
                known ? workOrder.getActualGoodQuantity() : null,
                workOrder.getActualScrapQuantity(),
                workOrder.getActualReworkQuantity(),
                workOrder.getCompletedQuantity(),
                open,
                known ? workOrder.availableToReceipt() : null,
                workOrder.getTerminalShortfallQuantity());
    }

    private static WipWorkOrderSummaryResponse summary(WorkOrder workOrder, WorkOrderReworkTotals rework) {
        boolean known = workOrder.isOperationTracking();
        BigDecimal pending = rework == null ? BigDecimal.ZERO : rework.getRework().subtract(rework.getRetryProcessed());
        return new WipWorkOrderSummaryResponse(
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                workOrder.getStatus(),
                workOrder.getVersion(),
                workOrder.getProductItem().getItemId(),
                workOrder.getProductItem().getCode(),
                workOrder.getProductItem().getName(),
                workOrder.getProductItem().getUnit(),
                workOrder.getPlannedQuantity(),
                known,
                known ? workOrder.getActualGoodQuantity() : null,
                workOrder.getActualScrapQuantity(),
                workOrder.getActualReworkQuantity(),
                pending,
                workOrder.getCompletedQuantity(),
                known ? workOrder.availableToReceipt() : null,
                workOrder.getTerminalShortfallQuantity(),
                workOrder.getCreatedAt());
    }
}
