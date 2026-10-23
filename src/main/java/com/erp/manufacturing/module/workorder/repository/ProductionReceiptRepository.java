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
import java.time.LocalDate;
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
     * Flat plant-scoped list (spec §6.2), with the G9a filters (FE §3.3): work order, destination warehouse,
     * received-on range (plant-local dates, inclusive) and a free-text search on the receipt code, work order code,
     * output item SKU/name and lot number. Every filter is optional and they combine with AND.
     *
     * <p>{@code join fetch w} is required, not an optimisation: unlike the nested endpoint above,
     * every row here can belong to a different work order, so rendering {@code workOrderCode} would
     * otherwise be one query per row. The {@code lines} collection is deliberately <em>not</em>
     * fetched (rule C13 forbids fetching a collection alongside pagination) — the service loads it
     * in one batch through {@code findByReceiptReceiptIdIn}.
     *
     * <p>🔴 Every nullable parameter that reaches {@code concat}/{@code like} or stands alone in {@code is null} is
     * cast — the {@code lower(bytea)} trap of CLAUDE.md §0.24 / §0.36.
     */
    @Query(value = """
            select r
            from ProductionReceipt r
            join fetch r.workOrder w
            where w.plant.plantId = :plantId
              and (:status is null or r.status = :status)
              and (:workOrderId is null or w.workOrderId = :workOrderId)
              and (cast(:receivedFrom as date) is null or r.receivedOn >= :receivedFrom)
              and (cast(:receivedTo as date) is null or r.receivedOn <= :receivedTo)
              and (:warehouseId is null or exists (
                    select 1 from ProductionReceiptLine wl
                    where wl.receipt = r and wl.warehouse.warehouseId = :warehouseId))
              and (cast(:search as string) is null
                   or lower(r.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(w.workOrderNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(w.productItem.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(w.productItem.name) like lower(concat('%', cast(:search as string), '%'))
                   or exists (
                        select 1 from ProductionReceiptLine sl left join sl.lot sll
                        where sl.receipt = r
                          and (lower(sl.requestedLotCode) like lower(concat('%', cast(:search as string), '%'))
                               or lower(sll.lotCode) like lower(concat('%', cast(:search as string), '%')))))
            """,
            countQuery = """
            select count(r)
            from ProductionReceipt r
            join r.workOrder w
            where w.plant.plantId = :plantId
              and (:status is null or r.status = :status)
              and (:workOrderId is null or w.workOrderId = :workOrderId)
              and (cast(:receivedFrom as date) is null or r.receivedOn >= :receivedFrom)
              and (cast(:receivedTo as date) is null or r.receivedOn <= :receivedTo)
              and (:warehouseId is null or exists (
                    select 1 from ProductionReceiptLine wl
                    where wl.receipt = r and wl.warehouse.warehouseId = :warehouseId))
              and (cast(:search as string) is null
                   or lower(r.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(w.workOrderNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(w.productItem.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(w.productItem.name) like lower(concat('%', cast(:search as string), '%'))
                   or exists (
                        select 1 from ProductionReceiptLine sl left join sl.lot sll
                        where sl.receipt = r
                          and (lower(sl.requestedLotCode) like lower(concat('%', cast(:search as string), '%'))
                               or lower(sll.lotCode) like lower(concat('%', cast(:search as string), '%')))))
            """)
    Page<ProductionReceipt> findByPlant(@Param("plantId") UUID plantId,
                                        @Param("status") ProductionReceiptStatus status,
                                        @Param("workOrderId") UUID workOrderId,
                                        @Param("warehouseId") UUID warehouseId,
                                        @Param("receivedFrom") LocalDate receivedFrom,
                                        @Param("receivedTo") LocalDate receivedTo,
                                        @Param("search") String search,
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

    /**
     * G9b: the quantity of receipts that still claim output (DRAFT, PENDING_APPROVAL, APPROVED) but recorded no
     * contribution — written before V84. Their sources are unknown, so the allocation charges them to the oldest
     * reports first, the order {@code sourceWipTraceIds} always assumed (D3). {@code excludeReceiptId}: a draft being
     * edited.
     */
    @Query("""
            select coalesce(sum(l.quantity), 0)
            from ProductionReceiptLine l
            where l.receipt.workOrder.workOrderId = :workOrderId
              and l.receipt.status in (
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.DRAFT,
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.PENDING_APPROVAL,
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED)
              and (:excludeReceiptId is null or l.receipt.receiptId <> :excludeReceiptId)
              and not exists (
                    select 1 from ProductionReceiptContribution c where c.receipt = l.receipt)
            """)
    BigDecimal sumLiveWithoutContributions(@Param("workOrderId") UUID workOrderId,
                                           @Param("excludeReceiptId") UUID excludeReceiptId);
}
