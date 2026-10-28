package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.GoodsReceiptLine;
import com.erp.manufacturing.module.purchasing.domain.GoodsReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, UUID> {

    /**
     * G7c: the purchase QC queue — lines of POSTED receipts that went on hold (G7b) and still have a
     * pending quantity. Receipts posted before G7b never went on hold and never appear. {@code search}
     * matches receipt number, order number, supplier code/name, item code/name and lot code. Oldest
     * receipt first, then id, so pages are stable. Nullable parameters are cast (§0.24, §0.36).
     */
    @EntityGraph(attributePaths = {"goodsReceipt", "goodsReceipt.purchaseOrder", "goodsReceipt.purchaseOrder.supplier",
            "goodsReceipt.warehouse", "item", "lot"})
    @Query(value = """
            select l
            from GoodsReceiptLine l
            join l.goodsReceipt gr
            join gr.purchaseOrder po
            left join l.lot lot
            where po.company.companyId = :companyId
              and po.plant.plantId = :plantId
              and gr.status = :posted
              and gr.qualityHeld = true
              and l.receivedQuantity - l.acceptedQuantity - l.rejectedQuantity > 0
              and (:warehouseId is null or gr.warehouse.warehouseId = :warehouseId)
              and (cast(:search as string) is null
                   or lower(gr.receiptNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(po.purchaseOrderNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(po.supplier.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(po.supplier.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(l.item.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(l.item.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(lot.lotCode, '')) like lower(concat('%', cast(:search as string), '%')))
              and (cast(:receivedDateFrom as date) is null or gr.receivedDate >= :receivedDateFrom)
              and (cast(:receivedDateTo as date) is null or gr.receivedDate <= :receivedDateTo)
            order by gr.receivedDate asc, gr.postedAt asc, l.goodsReceiptLineId asc
            """,
            countQuery = """
            select count(l)
            from GoodsReceiptLine l
            join l.goodsReceipt gr
            join gr.purchaseOrder po
            left join l.lot lot
            where po.company.companyId = :companyId
              and po.plant.plantId = :plantId
              and gr.status = :posted
              and gr.qualityHeld = true
              and l.receivedQuantity - l.acceptedQuantity - l.rejectedQuantity > 0
              and (:warehouseId is null or gr.warehouse.warehouseId = :warehouseId)
              and (cast(:search as string) is null
                   or lower(gr.receiptNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(po.purchaseOrderNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(po.supplier.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(po.supplier.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(l.item.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(l.item.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(lot.lotCode, '')) like lower(concat('%', cast(:search as string), '%')))
              and (cast(:receivedDateFrom as date) is null or gr.receivedDate >= :receivedDateFrom)
              and (cast(:receivedDateTo as date) is null or gr.receivedDate <= :receivedDateTo)
            """)
    Page<GoodsReceiptLine> searchQualityQueue(@Param("companyId") UUID companyId,
                                              @Param("plantId") UUID plantId,
                                              @Param("warehouseId") UUID warehouseId,
                                              @Param("search") String search,
                                              @Param("receivedDateFrom") LocalDate receivedDateFrom,
                                              @Param("receivedDateTo") LocalDate receivedDateTo,
                                              @Param("posted") GoodsReceiptStatus posted,
                                              Pageable pageable);

    /**
     * G7c (R13): a lot is the received lot of a POSTED, on-hold receipt line that purchase QC has not
     * finished. Such a lot may only leave HOLD through a QC decision.
     */
    @Query("""
            select count(l) > 0
            from GoodsReceiptLine l
            join l.goodsReceipt gr
            where l.lot.lotId = :lotId
              and gr.status = :posted
              and gr.qualityHeld = true
              and l.receivedQuantity - l.acceptedQuantity - l.rejectedQuantity > 0
            """)
    boolean existsPendingQualityForLot(@Param("lotId") UUID lotId, @Param("posted") GoodsReceiptStatus posted);

    /**
     * CF-3: what purchase QC still keeps in the quality hold of non-lot stock — pending (received − accepted − rejected)
     * and rejected quantity of lines of POSTED receipts that went on hold, per item and warehouse. A lot line is excluded:
     * its quantity lives on the lot, not in {@code qualityHoldQuantity}. Receipts posted before G7b never held anything.
     */
    @Query("""
            select l.item.itemId as itemId,
                   gr.warehouse.warehouseId as warehouseId,
                   coalesce(sum(l.receivedQuantity - l.acceptedQuantity - l.rejectedQuantity), 0) as pendingQuantity,
                   coalesce(sum(l.rejectedQuantity), 0) as rejectedQuantity
            from GoodsReceiptLine l
            join l.goodsReceipt gr
            where l.lot is null
              and gr.status = :posted
              and gr.qualityHeld = true
              and gr.warehouse.warehouseId in :warehouseIds
              and l.item.itemId in :itemIds
            group by l.item.itemId, gr.warehouse.warehouseId
            """)
    java.util.List<PurchaseQcHoldProjection> sumNonLotQualityHold(
            @Param("itemIds") java.util.Collection<UUID> itemIds,
            @Param("warehouseIds") java.util.Collection<UUID> warehouseIds,
            @Param("posted") GoodsReceiptStatus posted);
}
