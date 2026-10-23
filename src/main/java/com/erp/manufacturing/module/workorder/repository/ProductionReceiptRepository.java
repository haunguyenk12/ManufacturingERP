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
     * output item SKU/name and lot number. Every filter is optional and they combine with AND. G9c (FE Q21) adds
     * the quality filters: {@code qualityState} (same rule as {@code ProductionReceipt.qualityState}),
     * {@code pendingQc} (APPROVED with something still pending) and {@code hasQcDecision} (any decision at all, so a
     * history view keeps receipts judged only in part).
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
              and (cast(:pendingQc as boolean) is null
                   or (:pendingQc = true and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and exists (select 1 from ProductionReceiptLine qpm1 where qpm1.receipt = r and qpm1.quantity > qpm1.qcAcceptedQuantity + qpm1.qcRejectedQuantity))
                   or (:pendingQc = false and not (r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and exists (select 1 from ProductionReceiptLine qpm2 where qpm2.receipt = r and qpm2.quantity > qpm2.qcAcceptedQuantity + qpm2.qcRejectedQuantity))))
              and (cast(:hasQcDecision as boolean) is null
                   or (:hasQcDecision = true and (exists (select 1 from ProductionReceiptLine qam3 where qam3.receipt = r and qam3.qcAcceptedQuantity > 0) or exists (select 1 from ProductionReceiptLine qjm3 where qjm3.receipt = r and qjm3.qcRejectedQuantity > 0)))
                   or (:hasQcDecision = false and not (exists (select 1 from ProductionReceiptLine qam4 where qam4.receipt = r and qam4.qcAcceptedQuantity > 0) or exists (select 1 from ProductionReceiptLine qjm4 where qjm4.receipt = r and qjm4.qcRejectedQuantity > 0))))
              and (cast(:qualityState as string) is null
                   or (cast(:qualityState as string) = 'NOT_STARTED' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and not (exists (select 1 from ProductionReceiptLine qam5 where qam5.receipt = r and qam5.qcAcceptedQuantity > 0) or exists (select 1 from ProductionReceiptLine qjm5 where qjm5.receipt = r and qjm5.qcRejectedQuantity > 0)))
                   or (cast(:qualityState as string) = 'PARTIAL' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and (exists (select 1 from ProductionReceiptLine qam6 where qam6.receipt = r and qam6.qcAcceptedQuantity > 0) or exists (select 1 from ProductionReceiptLine qjm6 where qjm6.receipt = r and qjm6.qcRejectedQuantity > 0)) and exists (select 1 from ProductionReceiptLine qpm6 where qpm6.receipt = r and qpm6.quantity > qpm6.qcAcceptedQuantity + qpm6.qcRejectedQuantity))
                   or (cast(:qualityState as string) = 'ACCEPTED' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and not exists (select 1 from ProductionReceiptLine qpm7 where qpm7.receipt = r and qpm7.quantity > qpm7.qcAcceptedQuantity + qpm7.qcRejectedQuantity) and exists (select 1 from ProductionReceiptLine qam7 where qam7.receipt = r and qam7.qcAcceptedQuantity > 0) and not exists (select 1 from ProductionReceiptLine qjm7 where qjm7.receipt = r and qjm7.qcRejectedQuantity > 0))
                   or (cast(:qualityState as string) = 'REJECTED' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and not exists (select 1 from ProductionReceiptLine qpm8 where qpm8.receipt = r and qpm8.quantity > qpm8.qcAcceptedQuantity + qpm8.qcRejectedQuantity) and not exists (select 1 from ProductionReceiptLine qam8 where qam8.receipt = r and qam8.qcAcceptedQuantity > 0) and exists (select 1 from ProductionReceiptLine qjm8 where qjm8.receipt = r and qjm8.qcRejectedQuantity > 0))
                   or (cast(:qualityState as string) = 'MIXED' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and not exists (select 1 from ProductionReceiptLine qpm9 where qpm9.receipt = r and qpm9.quantity > qpm9.qcAcceptedQuantity + qpm9.qcRejectedQuantity) and exists (select 1 from ProductionReceiptLine qam9 where qam9.receipt = r and qam9.qcAcceptedQuantity > 0) and exists (select 1 from ProductionReceiptLine qjm9 where qjm9.receipt = r and qjm9.qcRejectedQuantity > 0)))
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
              and (cast(:pendingQc as boolean) is null
                   or (:pendingQc = true and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and exists (select 1 from ProductionReceiptLine qpc1 where qpc1.receipt = r and qpc1.quantity > qpc1.qcAcceptedQuantity + qpc1.qcRejectedQuantity))
                   or (:pendingQc = false and not (r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and exists (select 1 from ProductionReceiptLine qpc2 where qpc2.receipt = r and qpc2.quantity > qpc2.qcAcceptedQuantity + qpc2.qcRejectedQuantity))))
              and (cast(:hasQcDecision as boolean) is null
                   or (:hasQcDecision = true and (exists (select 1 from ProductionReceiptLine qac3 where qac3.receipt = r and qac3.qcAcceptedQuantity > 0) or exists (select 1 from ProductionReceiptLine qjc3 where qjc3.receipt = r and qjc3.qcRejectedQuantity > 0)))
                   or (:hasQcDecision = false and not (exists (select 1 from ProductionReceiptLine qac4 where qac4.receipt = r and qac4.qcAcceptedQuantity > 0) or exists (select 1 from ProductionReceiptLine qjc4 where qjc4.receipt = r and qjc4.qcRejectedQuantity > 0))))
              and (cast(:qualityState as string) is null
                   or (cast(:qualityState as string) = 'NOT_STARTED' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and not (exists (select 1 from ProductionReceiptLine qac5 where qac5.receipt = r and qac5.qcAcceptedQuantity > 0) or exists (select 1 from ProductionReceiptLine qjc5 where qjc5.receipt = r and qjc5.qcRejectedQuantity > 0)))
                   or (cast(:qualityState as string) = 'PARTIAL' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and (exists (select 1 from ProductionReceiptLine qac6 where qac6.receipt = r and qac6.qcAcceptedQuantity > 0) or exists (select 1 from ProductionReceiptLine qjc6 where qjc6.receipt = r and qjc6.qcRejectedQuantity > 0)) and exists (select 1 from ProductionReceiptLine qpc6 where qpc6.receipt = r and qpc6.quantity > qpc6.qcAcceptedQuantity + qpc6.qcRejectedQuantity))
                   or (cast(:qualityState as string) = 'ACCEPTED' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and not exists (select 1 from ProductionReceiptLine qpc7 where qpc7.receipt = r and qpc7.quantity > qpc7.qcAcceptedQuantity + qpc7.qcRejectedQuantity) and exists (select 1 from ProductionReceiptLine qac7 where qac7.receipt = r and qac7.qcAcceptedQuantity > 0) and not exists (select 1 from ProductionReceiptLine qjc7 where qjc7.receipt = r and qjc7.qcRejectedQuantity > 0))
                   or (cast(:qualityState as string) = 'REJECTED' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and not exists (select 1 from ProductionReceiptLine qpc8 where qpc8.receipt = r and qpc8.quantity > qpc8.qcAcceptedQuantity + qpc8.qcRejectedQuantity) and not exists (select 1 from ProductionReceiptLine qac8 where qac8.receipt = r and qac8.qcAcceptedQuantity > 0) and exists (select 1 from ProductionReceiptLine qjc8 where qjc8.receipt = r and qjc8.qcRejectedQuantity > 0))
                   or (cast(:qualityState as string) = 'MIXED' and r.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED and not exists (select 1 from ProductionReceiptLine qpc9 where qpc9.receipt = r and qpc9.quantity > qpc9.qcAcceptedQuantity + qpc9.qcRejectedQuantity) and exists (select 1 from ProductionReceiptLine qac9 where qac9.receipt = r and qac9.qcAcceptedQuantity > 0) and exists (select 1 from ProductionReceiptLine qjc9 where qjc9.receipt = r and qjc9.qcRejectedQuantity > 0)))
            """)
    Page<ProductionReceipt> findByPlant(@Param("plantId") UUID plantId,
                                        @Param("status") ProductionReceiptStatus status,
                                        @Param("workOrderId") UUID workOrderId,
                                        @Param("warehouseId") UUID warehouseId,
                                        @Param("receivedFrom") LocalDate receivedFrom,
                                        @Param("receivedTo") LocalDate receivedTo,
                                        @Param("search") String search,
                                        @Param("qualityState") String qualityState,
                                        @Param("pendingQc") Boolean pendingQc,
                                        @Param("hasQcDecision") Boolean hasQcDecision,
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
