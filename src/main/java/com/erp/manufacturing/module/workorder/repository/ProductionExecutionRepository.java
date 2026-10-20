package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /** G5 (FE Q16): output was already reported against this operation, i.e. it has started. */
    boolean existsByOperationWorkOrderOperationId(UUID workOrderOperationId);

    /**
     * G8c: what each operation of a work order has recorded, fresh work and retries of rework apart. The pools of an
     * operation are derived from this and the input ledger — never stored as counters that could drift from them.
     */
    @Query("""
            select e.operation.workOrderOperationId as operationId,
                   coalesce(sum(e.goodQuantity), 0) as good,
                   coalesce(sum(e.scrapQuantity), 0) as scrap,
                   coalesce(sum(e.reworkQuantity), 0) as rework,
                   coalesce(sum(case when e.sourceExecutionId is null
                                     then e.goodQuantity + e.scrapQuantity + e.reworkQuantity
                                     else cast(0 as BigDecimal) end), 0) as freshProcessed,
                   coalesce(sum(case when e.sourceExecutionId is not null
                                     then e.goodQuantity + e.scrapQuantity + e.reworkQuantity
                                     else cast(0 as BigDecimal) end), 0) as retryProcessed,
                   count(e) as executionCount
            from ProductionExecution e
            where e.workOrder.workOrderId = :workOrderId
              and e.operation is not null
            group by e.operation.workOrderOperationId
            """)
    List<OperationExecutionTotals> sumByOperation(@Param("workOrderId") UUID workOrderId);

    /** G8c: how much of each source execution's rework later executions already processed again. */
    @Query("""
            select e.sourceExecutionId as refId,
                   coalesce(sum(e.goodQuantity + e.scrapQuantity + e.reworkQuantity), 0) as quantity
            from ProductionExecution e
            where e.sourceExecutionId in :sourceExecutionIds
            group by e.sourceExecutionId
            """)
    List<KeyedQuantityProjection> sumRetryProcessedBySource(@Param("sourceExecutionIds") Collection<UUID> sourceExecutionIds);

    /**
     * G8d: rework recorded and rework processed again by retries, per work order of a WIP page — one query for the
     * page (C14). Pending rework of a work order = {@code rework − retryProcessed}: every retry processes rework of a
     * source of the same work order, never more than that source had pending.
     */
    @Query("""
            select e.workOrder.workOrderId as workOrderId,
                   coalesce(sum(e.reworkQuantity), 0) as rework,
                   coalesce(sum(case when e.sourceExecutionId is not null
                                     then e.goodQuantity + e.scrapQuantity + e.reworkQuantity
                                     else cast(0 as BigDecimal) end), 0) as retryProcessed
            from ProductionExecution e
            where e.workOrder.workOrderId in :workOrderIds
            group by e.workOrder.workOrderId
            """)
    List<WorkOrderReworkTotals> sumReworkByWorkOrderIds(@Param("workOrderIds") Collection<UUID> workOrderIds);
}
