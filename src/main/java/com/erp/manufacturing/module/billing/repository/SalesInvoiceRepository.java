package com.erp.manufacturing.module.billing.repository;

import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {

    @Query(value = "select nextval('sales_invoice_number_seq')", nativeQuery = true)
    long nextNumber();

    /**
     * Plant list with every filter optional. {@code search} matches the invoice code, the customer name or the order
     * number; {@code paymentState} keeps POSTED invoices in that state (G10d). 🔴 Each nullable String/date is cast in its
     * {@code is null} branch (§0.24, §0.36).
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
              and (cast(:paymentState as string) is null
                   or (i.status = com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus.POSTED
                       and (case when i.amount = 0 then 'NO_AMOUNT_DUE'
                                 when coalesce((select sum(a.amount) from CustomerPaymentAllocation a join a.payment p
                                                where a.invoiceId = i.invoiceId
                                                  and p.status = com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus.POSTED), 0) = 0
                                      then 'UNPAID'
                                 when coalesce((select sum(a.amount) from CustomerPaymentAllocation a join a.payment p
                                                where a.invoiceId = i.invoiceId
                                                  and p.status = com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus.POSTED), 0) >= i.amount
                                      then 'PAID'
                                 else 'PARTIALLY_PAID' end) = cast(:paymentState as string)))
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
              and (cast(:paymentState as string) is null
                   or (i.status = com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus.POSTED
                       and (case when i.amount = 0 then 'NO_AMOUNT_DUE'
                                 when coalesce((select sum(a.amount) from CustomerPaymentAllocation a join a.payment p
                                                where a.invoiceId = i.invoiceId
                                                  and p.status = com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus.POSTED), 0) = 0
                                      then 'UNPAID'
                                 when coalesce((select sum(a.amount) from CustomerPaymentAllocation a join a.payment p
                                                where a.invoiceId = i.invoiceId
                                                  and p.status = com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus.POSTED), 0) >= i.amount
                                      then 'PAID'
                                 else 'PARTIALLY_PAID' end) = cast(:paymentState as string)))
            """)
    Page<SalesInvoice> search(@Param("plantId") UUID plantId,
                              @Param("status") SalesInvoiceStatus status,
                              @Param("salesOrderId") UUID salesOrderId,
                              @Param("customerId") UUID customerId,
                              @Param("fromDate") LocalDate fromDate,
                              @Param("toDate") LocalDate toDate,
                              @Param("search") String search,
                              @Param("paymentState") String paymentState,
                              Pageable pageable);

    /** G10d: a customer's invoices of one plant in one status, oldest first — the payment candidates. */
    List<SalesInvoice> findByPlantIdAndCustomerIdAndStatusOrderByDocumentDateAscCodeAsc(UUID plantId, UUID customerId,
                                                                                         SalesInvoiceStatus status);

    /** G10d: an order's invoices in one status — the order's receivable. */
    List<SalesInvoice> findBySalesOrderIdAndStatus(UUID salesOrderId, SalesInvoiceStatus status);

    /**
     * G10d: locks invoice rows in one stable order before their balances are read, so two payments competing for the
     * same outstanding amount queue instead of both reading it (FE A37).
     */
    @Query(value = "select invoice_id from sales_invoices where invoice_id in (:ids) order by invoice_id for update",
            nativeQuery = true)
    List<UUID> lockRows(@Param("ids") Collection<UUID> ids);
}
