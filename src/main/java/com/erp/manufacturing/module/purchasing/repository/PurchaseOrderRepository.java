package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.PurchaseOrder;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    boolean existsByCompanyCompanyIdAndPurchaseOrderNo(UUID companyId, String purchaseOrderNo);

    /**
     * G7a adds {@code search} (number, reference, supplier code or name) and an inclusive
     * {@code orderDate} range. Every nullable parameter is cast: an untyped {@code null} inside
     * {@code concat}/{@code like} makes Postgres pick {@code bytea} and fail the whole query
     * ({@code §0.24}), and a date only seen in {@code is null} has no type at all ({@code §0.36}).
     */
    @EntityGraph(attributePaths = {"company", "plant", "warehouse", "supplier"})
    @Query("""
            select p
            from PurchaseOrder p
            where p.company.companyId = :companyId
              and p.plant.plantId = :plantId
              and (:warehouseId is null or p.warehouse.warehouseId = :warehouseId)
              and (:supplierId is null or p.supplier.supplierId = :supplierId)
              and (:status is null or p.status = :status)
              and (cast(:search as string) is null
                   or lower(p.purchaseOrderNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(p.reference, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(p.supplier.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(p.supplier.name) like lower(concat('%', cast(:search as string), '%')))
              and (cast(:orderDateFrom as date) is null or p.orderDate >= :orderDateFrom)
              and (cast(:orderDateTo as date) is null or p.orderDate <= :orderDateTo)
            """)
    Page<PurchaseOrder> search(@Param("companyId") UUID companyId,
                               @Param("plantId") UUID plantId,
                               @Param("warehouseId") UUID warehouseId,
                               @Param("supplierId") UUID supplierId,
                               @Param("status") PurchaseOrderStatus status,
                               @Param("search") String search,
                               @Param("orderDateFrom") LocalDate orderDateFrom,
                               @Param("orderDateTo") LocalDate orderDateTo,
                               Pageable pageable);

    /**
     * G7b (D11): {@code SELECT … FOR UPDATE} on the purchase order row, held to the end of the
     * caller's transaction. Native and id-only on purpose: it hydrates no entity, so it never checks
     * the version of a purchase order already in the persistence context — the caller re-reads the
     * order afterwards ({@code GoodsReceiptPoster.lockOrder}).
     */
    @Query(value = "select purchase_order_id from purchase_orders where purchase_order_id = :purchaseOrderId for update",
            nativeQuery = true)
    Optional<UUID> lockRowByPurchaseOrderId(@Param("purchaseOrderId") UUID purchaseOrderId);

    /**
     * G7b: purchase orders a goods receipt can be drafted against — CONFIRMED/PARTIALLY_RECEIVED with
     * at least one line still open. Lines are loaded per page in a separate batch (C13).
     */
    @EntityGraph(attributePaths = {"supplier", "warehouse"})
    @Query("""
            select p
            from PurchaseOrder p
            where p.company.companyId = :companyId
              and p.plant.plantId = :plantId
              and p.status in :statuses
              and exists (select l.purchaseOrderLineId from PurchaseOrderLine l
                          where l.purchaseOrder = p
                            and l.orderedQuantity - l.receivedQuantity - l.closedQuantity > 0)
              and (cast(:search as string) is null
                   or lower(p.purchaseOrderNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(p.reference, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(p.supplier.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(p.supplier.name) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<PurchaseOrder> searchReceivingCandidates(@Param("companyId") UUID companyId,
                                                  @Param("plantId") UUID plantId,
                                                  @Param("statuses") Collection<PurchaseOrderStatus> statuses,
                                                  @Param("search") String search,
                                                  Pageable pageable);

    /** Next value of {@code purchase_order_number_seq} (V76) — concurrent drafts never share a number. */
    @Query(value = "select nextval('purchase_order_number_seq')", nativeQuery = true)
    long nextOrderNumber();

    @EntityGraph(attributePaths = {
            "company",
            "plant",
            "warehouse",
            "supplier",
            "sourceRequisition",
            "lines",
            "lines.item",
            "lines.purchaseRequisitionLine"
    })
    Optional<PurchaseOrder> findWithDetailsByPurchaseOrderId(UUID purchaseOrderId);

    @Query("""
            select l.item.itemId as itemId,
                   coalesce(sum(l.orderedQuantity - l.receivedQuantity - l.closedQuantity), 0) as openSupplyQuantity
            from PurchaseOrderLine l
            join l.purchaseOrder o
            where o.company.companyId = :companyId
              and o.plant.plantId = :plantId
              and l.item.itemId in :itemIds
              and o.warehouse.warehouseId in :warehouseIds
              and o.status in :statuses
              and l.orderedQuantity - l.receivedQuantity - l.closedQuantity > 0
            group by l.item.itemId
            """)
    List<PurchaseOrderSupplyProjection> aggregateOpenSupply(
            @Param("companyId") UUID companyId,
            @Param("plantId") UUID plantId,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("statuses") Collection<PurchaseOrderStatus> statuses);
}
