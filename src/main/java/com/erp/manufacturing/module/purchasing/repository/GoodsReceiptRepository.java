package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.GoodsReceipt;
import com.erp.manufacturing.module.purchasing.domain.GoodsReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    Optional<GoodsReceipt> findByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(UUID purchaseOrderId, String idempotencyKey);

    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "purchaseOrder.supplier",
            "warehouse",
            "lines",
            "lines.purchaseOrderLine",
            "lines.item",
            "lines.lot",
            "lines.stockMovement"
    })
    Optional<GoodsReceipt> findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
            UUID purchaseOrderId, String idempotencyKey);

    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.supplier", "warehouse"})
    Page<GoodsReceipt> findByPurchaseOrderPurchaseOrderId(UUID purchaseOrderId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "purchaseOrder.supplier",
            "warehouse",
            "lines",
            "lines.purchaseOrderLine",
            "lines.item",
            "lines.lot",
            "lines.stockMovement"
    })
    Optional<GoodsReceipt> findWithDetailsByGoodsReceiptId(UUID goodsReceiptId);

    /**
     * G7b: the order a receipt belongs to, without loading either entity — posting must lock the
     * purchase order before anything of it enters the persistence context.
     */
    @Query("select gr.purchaseOrder.purchaseOrderId from GoodsReceipt gr where gr.goodsReceiptId = :goodsReceiptId")
    Optional<UUID> findPurchaseOrderIdByGoodsReceiptId(@Param("goodsReceiptId") UUID goodsReceiptId);

    /** G7b (FE Q11): a DRAFT receipt still pending against the order blocks cancelling or closing it. */
    boolean existsByPurchaseOrderPurchaseOrderIdAndStatus(UUID purchaseOrderId, GoodsReceiptStatus status);

    /**
     * G7b: plant-wide receipt list. {@code search} matches the receipt number, the order number, the
     * reference and the supplier code or name; {@code receivedDate} range inclusive. Nullable
     * parameters are cast (§0.24, §0.36).
     */
    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.supplier", "warehouse"})
    @Query("""
            select gr
            from GoodsReceipt gr
            join gr.purchaseOrder po
            where po.company.companyId = :companyId
              and po.plant.plantId = :plantId
              and (:purchaseOrderId is null or po.purchaseOrderId = :purchaseOrderId)
              and (:warehouseId is null or gr.warehouse.warehouseId = :warehouseId)
              and (:status is null or gr.status = :status)
              and (cast(:search as string) is null
                   or lower(gr.receiptNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(po.purchaseOrderNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(gr.reference, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(po.supplier.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(po.supplier.name) like lower(concat('%', cast(:search as string), '%')))
              and (cast(:receivedDateFrom as date) is null or gr.receivedDate >= :receivedDateFrom)
              and (cast(:receivedDateTo as date) is null or gr.receivedDate <= :receivedDateTo)
            """)
    Page<GoodsReceipt> searchByPlant(@Param("companyId") UUID companyId,
                                     @Param("plantId") UUID plantId,
                                     @Param("purchaseOrderId") UUID purchaseOrderId,
                                     @Param("warehouseId") UUID warehouseId,
                                     @Param("status") GoodsReceiptStatus status,
                                     @Param("search") String search,
                                     @Param("receivedDateFrom") LocalDate receivedDateFrom,
                                     @Param("receivedDateTo") LocalDate receivedDateTo,
                                     Pageable pageable);

    /**
     * G7c: {@code SELECT … FOR UPDATE} on the receipt row, id only — the purchase QC guard has already
     * loaded the receipt into the request's EntityManager, so the caller re-reads it after this (B194).
     */
    @Query(value = "select goods_receipt_id from goods_receipts where goods_receipt_id = :goodsReceiptId for update",
            nativeQuery = true)
    Optional<UUID> lockRowByGoodsReceiptId(@Param("goodsReceiptId") UUID goodsReceiptId);

    /** Next value of {@code goods_receipt_number_seq} (V77). */
    @Query(value = "select nextval('goods_receipt_number_seq')", nativeQuery = true)
    long nextReceiptNumber();

    /** Server numbers skip a value a legacy, client-chosen receipt number already took in the company. */
    boolean existsByPurchaseOrderCompanyCompanyIdAndReceiptNo(UUID companyId, String receiptNo);
}
