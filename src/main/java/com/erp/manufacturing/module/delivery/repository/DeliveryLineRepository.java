package com.erp.manufacturing.module.delivery.repository;

import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DeliveryLineRepository extends JpaRepository<DeliveryLine, UUID> {

    List<DeliveryLine> findByDeliveryDeliveryIdOrderByLineNo(UUID deliveryId);

    List<DeliveryLine> findByDeliveryDeliveryIdInOrderByLineNo(Collection<UUID> deliveryIds);

    /**
     * Bulk delete, run at once ({@code flushAutomatically}) so a full replacement can reuse {@code line_no} 1..N
     * without colliding with {@code uk_delivery_lines_delivery_line_no} (the orphan-removal trap of §0.40).
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from DeliveryLine l where l.delivery.deliveryId = :deliveryId")
    int deleteByDeliveryId(@Param("deliveryId") UUID deliveryId);

    /** D per sales order line: what POSTED deliveries took (one query, C14). */
    @Query("""
            select l.salesOrderLineId as salesOrderLineId, sum(l.quantity) as quantity
            from DeliveryLine l join l.delivery d
            where l.salesOrderLineId in :lineIds
              and d.status = com.erp.manufacturing.module.delivery.domain.DeliveryStatus.POSTED
            group by l.salesOrderLineId
            """)
    List<LineQuantityProjection> sumPostedBySalesOrderLineIds(@Param("lineIds") Collection<UUID> lineIds);

    /**
     * Cd per sales order line: what DRAFT deliveries claim, the delivery being edited excluded (FE R06) — by id, so a
     * draft never counts its own claim twice.
     */
    @Query("""
            select l.salesOrderLineId as salesOrderLineId, sum(l.quantity) as quantity
            from DeliveryLine l join l.delivery d
            where l.salesOrderLineId in :lineIds
              and d.status = com.erp.manufacturing.module.delivery.domain.DeliveryStatus.DRAFT
              and (:excludeDeliveryId is null or d.deliveryId <> :excludeDeliveryId)
            group by l.salesOrderLineId
            """)
    List<LineQuantityProjection> sumDraftBySalesOrderLineIds(@Param("lineIds") Collection<UUID> lineIds,
                                                            @Param("excludeDeliveryId") UUID excludeDeliveryId);

    /** Lines by id with their delivery — what an invoice line points at (G10c). */
    @Query("select l from DeliveryLine l join fetch l.delivery d where l.deliveryLineId in :ids")
    List<DeliveryLine> findWithDeliveryByDeliveryLineIdIn(@Param("ids") Collection<UUID> ids);

    /** Every line of the order's POSTED deliveries, oldest document first — the invoice candidates (G10c). */
    @Query("""
            select l from DeliveryLine l join fetch l.delivery d
            where d.salesOrderId = :salesOrderId
              and d.status = com.erp.manufacturing.module.delivery.domain.DeliveryStatus.POSTED
            order by d.documentDate, d.code, l.lineNo
            """)
    List<DeliveryLine> findPostedBySalesOrderId(@Param("salesOrderId") UUID salesOrderId);

    interface LineQuantityProjection {
        UUID getSalesOrderLineId();

        BigDecimal getQuantity();
    }

    /** G11b: lines of POSTED deliveries of one item from one warehouse, in posting order then line number. */
    @Query("""
            select l from DeliveryLine l
            join fetch l.delivery d
            where l.itemId = :itemId and l.warehouseId = :warehouseId
              and d.status = com.erp.manufacturing.module.delivery.domain.DeliveryStatus.POSTED
            order by d.postedAt, d.deliveryId, l.lineNo
            """)
    List<DeliveryLine> findPostedByItemAndWarehouse(@Param("itemId") UUID itemId, @Param("warehouseId") UUID warehouseId);
}
