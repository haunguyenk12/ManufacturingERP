package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.operation.WorkOrderOperationProgressResponse;
import com.erp.manufacturing.module.workorder.repository.KeyedQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.OperationExecutionTotals;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationInputRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * G8c (D1): the pools of every operation of a work order, derived from the input ledger and the executions recorded
 * against each operation — four queries per work order, whatever its number of operations (C14). The only stored
 * fact about an operation's progress is its explicit finish ({@link WorkOrderOperation#getCompletedAt()}); nothing
 * here is a counter that could drift from the ledgers (invariant B217).
 */
@Component
@RequiredArgsConstructor
class OperationLedger {

    private final WorkOrderOperationRepository operationRepository;
    private final WorkOrderOperationInputRepository inputRepository;
    private final ProductionExecutionRepository executionRepository;

    /** Every operation of the work order in sequence order, with its pools. */
    List<OperationState> load(UUID workOrderId) {
        List<WorkOrderOperation> operations = operationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(workOrderId);
        if (operations.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> inputs = byRef(inputRepository.sumInputByOperation(workOrderId));
        Map<UUID, BigDecimal> transferred = byRef(inputRepository.sumTransferredBySourceOperation(workOrderId));
        Map<UUID, OperationExecutionTotals> totals = executionRepository.sumByOperation(workOrderId).stream()
                .collect(Collectors.toMap(OperationExecutionTotals::getOperationId, Function.identity()));
        List<OperationState> states = new ArrayList<>(operations.size());
        for (int i = 0; i < operations.size(); i++) {
            WorkOrderOperation operation = operations.get(i);
            UUID id = operation.getWorkOrderOperationId();
            OperationExecutionTotals total = totals.get(id);
            states.add(new OperationState(
                    operation,
                    i == 0 ? null : operations.get(i - 1),
                    i == operations.size() - 1,
                    inputs.getOrDefault(id, BigDecimal.ZERO),
                    total == null ? BigDecimal.ZERO : total.getFreshProcessed(),
                    total == null ? BigDecimal.ZERO : total.getGood(),
                    total == null ? BigDecimal.ZERO : total.getScrap(),
                    total == null ? BigDecimal.ZERO : total.getRework(),
                    total == null ? BigDecimal.ZERO : total.getRetryProcessed(),
                    transferred.getOrDefault(id, BigDecimal.ZERO),
                    total == null ? 0L : total.getExecutionCount()));
        }
        return states;
    }

    /** The state of one operation, which must belong to the work order. */
    static OperationState find(List<OperationState> states, UUID operationId) {
        return states.stream()
                .filter(state -> state.operation().getWorkOrderOperationId().equals(operationId))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order operation", operationId));
    }

    /** The state of the operation right before {@code state}; {@code null} on the first operation. */
    static OperationState predecessorOf(List<OperationState> states, OperationState state) {
        int index = states.indexOf(state);
        return index <= 0 ? null : states.get(index - 1);
    }

    static WorkOrderOperationProgressResponse toResponse(OperationState state, WorkOrder workOrder) {
        boolean known = workOrder.isOperationTracking();
        WorkOrderOperation operation = state.operation();
        return new WorkOrderOperationProgressResponse(
                operation.getWorkOrderOperationId(),
                operation.getSequence(),
                operation.getName(),
                operation.getWorkCenterCode(),
                state.status(),
                known,
                known ? state.input() : null,
                state.freshProcessed(),
                known ? state.freshRemaining() : null,
                state.good(),
                state.scrap(),
                state.rework(),
                state.pendingRework(),
                known && !state.last() ? state.transferred() : null,
                known && !state.last() ? state.transferable() : null,
                state.executionCount(),
                operation.getCompletedAt(),
                operation.getCompletedBy(),
                workOrder.getVersion());
    }

    private static Map<UUID, BigDecimal> byRef(List<KeyedQuantityProjection> rows) {
        return rows.stream().collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
    }

    /** One operation and its pools. Quantities are never null. */
    record OperationState(WorkOrderOperation operation,
                          WorkOrderOperation predecessor,
                          boolean last,
                          BigDecimal input,
                          BigDecimal freshProcessed,
                          BigDecimal good,
                          BigDecimal scrap,
                          BigDecimal rework,
                          BigDecimal retryProcessed,
                          BigDecimal transferred,
                          long executionCount) {

        boolean first() {
            return predecessor == null;
        }

        boolean predecessorCompleted() {
            return predecessor == null || predecessor.isCompleted();
        }

        /** {@code input − fresh processed} (FE §3.1): retries never draw on it. */
        BigDecimal freshRemaining() {
            return input.subtract(freshProcessed);
        }

        /** {@code historical rework − processed by retries}: retries reference sources of the same operation only. */
        BigDecimal pendingRework() {
            return rework.subtract(retryProcessed);
        }

        /** {@code good − transferred}: what the next operation may still take as input. */
        BigDecimal transferable() {
            return good.subtract(transferred);
        }

        String status() {
            if (operation.isCompleted()) {
                return "COMPLETED";
            }
            if (input.signum() > 0 || executionCount > 0) {
                return "IN_PROGRESS";
            }
            return predecessorCompleted() ? "READY" : "PENDING";
        }
    }
}
