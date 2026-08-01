package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionExecutionRepository extends JpaRepository<ProductionExecution, UUID> {

    @EntityGraph(attributePaths = {"workOrder", "workOrder.productItem", "operation"})
    Optional<ProductionExecution> findWithDetailsByIdempotencyKey(String idempotencyKey);

    /**
     * {@code workOrder} and {@code workOrder.productItem} are fetched, not just {@code operation}
     * (widened in F8). Every work-order-derived field on {@code ProductionExecutionResponse} — the
     * cumulative totals, {@code uom}, {@code workOrderCode}, {@code workOrderCompletionPercent} —
     * dereferences {@code execution.getWorkOrder()}, so without them a page of executions is N+1.
     *
     * <p>Easy to miss on this endpoint specifically: every row here shares one work order, so the
     * first-level cache hides the problem. It shows up on the flat
     * {@code GET /production-executions} list, where the rows span work orders.
     */
    @EntityGraph(attributePaths = {"operation", "workOrder", "workOrder.productItem"})
    Page<ProductionExecution> findByWorkOrderWorkOrderId(UUID workOrderId, Pageable pageable);

    /**
     * Chronological shop-floor history. Receipts consume it FIFO to work out which reports fed a
     * given receipt ({@code sourceWipTraceIds}, spec §6.4).
     */
    List<ProductionExecution> findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(UUID workOrderId);
}
