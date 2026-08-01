package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionReceipt;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionReceiptRepository extends JpaRepository<ProductionReceipt, UUID> {

    Optional<ProductionReceipt> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"workOrder", "lines", "lines.item", "lines.warehouse", "lines.lot", "lines.stockMovement"})
    Optional<ProductionReceipt> findWithLinesByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"workOrder", "lines", "lines.item", "lines.warehouse", "lines.lot", "lines.stockMovement"})
    Optional<ProductionReceipt> findWithLinesByReceiptId(UUID receiptId);

    Page<ProductionReceipt> findByWorkOrderWorkOrderId(UUID workOrderId, Pageable pageable);

    /**
     * Flat plant-scoped list (spec §6.2). {@code status} is an optional filter.
     *
     * <p>{@code join fetch w} is required, not an optimisation: unlike the nested endpoint above,
     * every row here can belong to a different work order, so rendering {@code workOrderCode} would
     * otherwise be one query per row. The {@code lines} collection is deliberately <em>not</em>
     * fetched (rule C13 forbids fetching a collection alongside pagination) — the service loads it
     * in one batch through {@code findByReceiptReceiptIdIn}.
     */
    @Query(value = """
            select r
            from ProductionReceipt r
            join fetch r.workOrder w
            where w.plant.plantId = :plantId
              and (:status is null or r.status = :status)
            """,
            countQuery = """
            select count(r)
            from ProductionReceipt r
            where r.workOrder.plant.plantId = :plantId
              and (:status is null or r.status = :status)
            """)
    Page<ProductionReceipt> findByPlant(@Param("plantId") UUID plantId,
                                        @Param("status") ProductionReceiptStatus status,
                                        Pageable pageable);

    /**
     * Batch form of {@link #sumOpenQuantityByWorkOrderId} — one query for a whole page of candidate
     * work orders instead of one per row (rule C14). Work orders with no open receipt are simply
     * absent from the result; callers read them as zero.
     */
    @Query("""
            select l.receipt.workOrder.workOrderId as workOrderId,
                   coalesce(sum(l.quantity), 0) as openQuantity
            from ProductionReceiptLine l
            where l.receipt.workOrder.workOrderId in :workOrderIds
              and l.receipt.status in (
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.DRAFT,
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.PENDING_APPROVAL)
            group by l.receipt.workOrder.workOrderId
            """)
    List<OpenReceiptQuantityProjection> sumOpenQuantityByWorkOrderIds(
            @Param("workOrderIds") Collection<UUID> workOrderIds);

    /**
     * Quantity already claimed by receipts that are still open — {@code DRAFT} as well as
     * {@code PENDING_APPROVAL}. Must be deducted from the remaining planned quantity so two open
     * receipts cannot together exceed what the work order plans to produce (invariant B16).
     * {@code DRAFT} was added in F2: without it two drafts could each pass the check and then both
     * be submitted.
     */
    @Query("""
            select coalesce(sum(l.quantity), 0)
            from ProductionReceiptLine l
            where l.receipt.workOrder.workOrderId = :workOrderId
              and l.receipt.status in (
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.DRAFT,
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.PENDING_APPROVAL)
            """)
    BigDecimal sumOpenQuantityByWorkOrderId(@Param("workOrderId") UUID workOrderId);
}
