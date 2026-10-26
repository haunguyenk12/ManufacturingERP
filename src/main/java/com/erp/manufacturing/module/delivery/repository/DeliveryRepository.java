package com.erp.manufacturing.module.delivery.repository;

import com.erp.manufacturing.module.delivery.domain.Delivery;
import com.erp.manufacturing.module.delivery.domain.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    @Query(value = "select nextval('delivery_number_seq')", nativeQuery = true)
    long nextNumber();

    boolean existsBySalesOrderIdAndStatusIn(UUID salesOrderId, Collection<DeliveryStatus> statuses);

    /**
     * Plant list with every filter optional. {@code search} matches the delivery code, the customer name, the order
     * number or an item code on one of its lines; {@code warehouseId} keeps deliveries with a line there.
     * 🔴 Each nullable String/date is cast in its {@code is null} branch (§0.24, §0.36).
     */
    @Query(value = """
            select d from Delivery d
            where d.plantId = :plantId
              and (:status is null or d.status = :status)
              and (:salesOrderId is null or d.salesOrderId = :salesOrderId)
              and (:customerId is null or d.customerId = :customerId)
              and (cast(:fromDate as date) is null or d.documentDate >= :fromDate)
              and (cast(:toDate as date) is null or d.documentDate <= :toDate)
              and (:warehouseId is null or exists (
                    select 1 from DeliveryLine wl where wl.delivery = d and wl.warehouseId = :warehouseId))
              and (cast(:search as string) is null
                   or lower(d.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(d.customerName) like lower(concat('%', cast(:search as string), '%'))
                   or exists (select 1 from SalesOrder so where so.salesOrderId = d.salesOrderId
                              and lower(so.orderNo) like lower(concat('%', cast(:search as string), '%')))
                   or exists (select 1 from DeliveryLine sl, Item i where sl.delivery = d and i.itemId = sl.itemId
                              and lower(i.code) like lower(concat('%', cast(:search as string), '%'))))
            """,
            countQuery = """
            select count(d) from Delivery d
            where d.plantId = :plantId
              and (:status is null or d.status = :status)
              and (:salesOrderId is null or d.salesOrderId = :salesOrderId)
              and (:customerId is null or d.customerId = :customerId)
              and (cast(:fromDate as date) is null or d.documentDate >= :fromDate)
              and (cast(:toDate as date) is null or d.documentDate <= :toDate)
              and (:warehouseId is null or exists (
                    select 1 from DeliveryLine wl where wl.delivery = d and wl.warehouseId = :warehouseId))
              and (cast(:search as string) is null
                   or lower(d.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(d.customerName) like lower(concat('%', cast(:search as string), '%'))
                   or exists (select 1 from SalesOrder so where so.salesOrderId = d.salesOrderId
                              and lower(so.orderNo) like lower(concat('%', cast(:search as string), '%')))
                   or exists (select 1 from DeliveryLine sl, Item i where sl.delivery = d and i.itemId = sl.itemId
                              and lower(i.code) like lower(concat('%', cast(:search as string), '%'))))
            """)
    Page<Delivery> search(@Param("plantId") UUID plantId,
                          @Param("status") DeliveryStatus status,
                          @Param("salesOrderId") UUID salesOrderId,
                          @Param("customerId") UUID customerId,
                          @Param("warehouseId") UUID warehouseId,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate,
                          @Param("search") String search,
                          Pageable pageable);

    /** POSTED deliveries of one plant with a line taken from one of {@code lotIds} (FE "related"). */
    @Query(value = """
            select d from Delivery d
            where d.plantId = :plantId
              and d.status = com.erp.manufacturing.module.delivery.domain.DeliveryStatus.POSTED
              and exists (select 1 from DeliveryLine l where l.delivery = d and l.lotId in :lotIds)
            """,
            countQuery = """
            select count(d) from Delivery d
            where d.plantId = :plantId
              and d.status = com.erp.manufacturing.module.delivery.domain.DeliveryStatus.POSTED
              and exists (select 1 from DeliveryLine l where l.delivery = d and l.lotId in :lotIds)
            """)
    Page<Delivery> findPostedFromLots(@Param("plantId") UUID plantId,
                                      @Param("lotIds") Collection<UUID> lotIds,
                                      Pageable pageable);
}
