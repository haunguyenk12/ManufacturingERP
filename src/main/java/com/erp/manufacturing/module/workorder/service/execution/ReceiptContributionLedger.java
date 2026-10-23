package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.repository.KeyedQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptContributionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * G9b (D3, FE R05/Q6): which final-good executions a receipt draws from. The server allocates, oldest report first
 * ({@code actualEndedAt}, then id) — a client never names its sources. Every execution can be claimed at most up to its
 * own good, counting every receipt that still claims output (DRAFT, PENDING_APPROVAL, APPROVED). Receipts written
 * before V84 have no contribution rows; their quantity is charged to the oldest reports first — the order
 * {@code sourceWipTraceIds} always assumed — so new receipts never draw on the same output twice (B231).
 *
 * <p>The final good of a work order is the good of its last operation (B219): only executions of that operation are
 * sources. A work order without operations, or one reported before operations were tracked (V81), counts every
 * execution — exactly what {@code WorkOrder.actualGoodQuantity} counted for it.
 */
@Component
@RequiredArgsConstructor
class ReceiptContributionLedger {

    private final ProductionExecutionRepository executionRepository;
    private final WorkOrderOperationRepository operationRepository;
    private final ProductionReceiptContributionRepository contributionRepository;
    private final ProductionReceiptRepository receiptRepository;

    record Share(ProductionExecution execution, BigDecimal quantity) {}

    /** Executions whose good is final good, oldest report first. */
    List<ProductionExecution> finalGoodSources(WorkOrder workOrder) {
        UUID lastOperationId = null;
        if (workOrder.isOperationTracking()) {
            List<WorkOrderOperation> operations =
                    operationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(workOrder.getWorkOrderId());
            if (!operations.isEmpty()) {
                lastOperationId = operations.get(operations.size() - 1).getWorkOrderOperationId();
            }
        }
        UUID last = lastOperationId;
        return executionRepository.findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrder.getWorkOrderId()).stream()
                .filter(execution -> execution.getGoodQuantity().signum() > 0)
                .filter(execution -> last == null || (execution.getOperation() != null
                        && last.equals(execution.getOperation().getWorkOrderOperationId())))
                .sorted(Comparator.comparing((ProductionExecution execution) -> reportedAt(execution))
                        .thenComparing(execution -> execution.getProductionExecutionId().toString()))
                .toList();
    }

    /**
     * Takes {@code quantity} from what is still unclaimed, oldest report first. {@code excludeReceiptId} gives one
     * receipt's own claims back first — the draft being edited.
     */
    List<Share> allocate(WorkOrder workOrder, BigDecimal quantity, UUID excludeReceiptId) {
        UUID workOrderId = workOrder.getWorkOrderId();
        Map<UUID, BigDecimal> claimed = contributionRepository.sumLiveByExecution(workOrderId, excludeReceiptId).stream()
                .collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
        BigDecimal unrecorded = receiptRepository.sumLiveWithoutContributions(workOrderId, excludeReceiptId);
        BigDecimal left = quantity;
        List<Share> shares = new ArrayList<>();
        for (ProductionExecution execution : finalGoodSources(workOrder)) {
            BigDecimal free = execution.getGoodQuantity()
                    .subtract(claimed.getOrDefault(execution.getProductionExecutionId(), BigDecimal.ZERO));
            BigDecimal charged = free.min(unrecorded).max(BigDecimal.ZERO);
            unrecorded = unrecorded.subtract(charged);
            free = free.subtract(charged);
            if (free.signum() <= 0) {
                continue;
            }
            BigDecimal take = free.min(left);
            shares.add(new Share(execution, take));
            left = left.subtract(take);
            if (left.signum() == 0) {
                return shares;
            }
        }
        // The quota check ran first, so the reports always cover the receipt; this guards against ledgers that drifted.
        throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, Map.of("quantity",
                "the final-good reports of the work order no longer cover " + quantity.toPlainString()));
    }

    private static Instant reportedAt(ProductionExecution execution) {
        return execution.getActualEndedAt() != null ? execution.getActualEndedAt() : execution.getCreatedAt();
    }
}
