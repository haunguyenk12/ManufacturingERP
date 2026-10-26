package com.erp.manufacturing.module.billing.repository;

import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {

    @Query(value = "select nextval('sales_invoice_number_seq')", nativeQuery = true)
    long nextNumber();

    /**
     * Plant list with every filter optional. {@code search} matches the invoice code, the customer name or the order
     * number. 🔴 Each nullable String/date is cast in its {@code is null} branch (§0.24, §0.36).
     */
    @Query(value = """
            select i from SalesInvoice i
            where i.plantId = :plantId
              and (:status is null or i.status = :status)
              and (:salesOrderId is null or i.salesOrderId = :salesOrderId)
              and (:customerId is null or i.customerId = :customerId)
              and (cast(:fromDate as date) is null or i.documentDate >= :fromDate)
              and (cast(:toDate as date) is null or i.documentDate <= :toDate)
              and (cast(:search as string) is null
                   or lower(i.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(i.customerName) like lower(concat('%', cast(:search as string), '%'))
                   or exists (select 1 from SalesOrder so where so.salesOrderId = i.salesOrderId
                              and lower(so.orderNo) like lower(concat('%', cast(:search as string), '%'))))
            """,
            countQuery = """
            select count(i) from SalesInvoice i
            where i.plantId = :plantId
              and (:status is null or i.status = :status)
              and (:salesOrderId is null or i.salesOrderId = :salesOrderId)
              and (:customerId is null or i.customerId = :customerId)
              and (cast(:fromDate as date) is null or i.documentDate >= :fromDate)
              and (cast(:toDate as date) is null or i.documentDate <= :toDate)
              and (cast(:search as string) is null
                   or lower(i.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(i.customerName) like lower(concat('%', cast(:search as string), '%'))
                   or exists (select 1 from SalesOrder so where so.salesOrderId = i.salesOrderId
                              and lower(so.orderNo) like lower(concat('%', cast(:search as string), '%'))))
            """)
    Page<SalesInvoice> search(@Param("plantId") UUID plantId,
                              @Param("status") SalesInvoiceStatus status,
                              @Param("salesOrderId") UUID salesOrderId,
                              @Param("customerId") UUID customerId,
                              @Param("fromDate") LocalDate fromDate,
                              @Param("toDate") LocalDate toDate,
                              @Param("search") String search,
                              Pageable pageable);
}
