package com.erp.manufacturing.module.billing.repository;

import com.erp.manufacturing.module.billing.domain.CustomerPayment;
import com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus;
import com.erp.manufacturing.module.billing.domain.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, UUID> {

    @Query(value = "select nextval('customer_payment_number_seq')", nativeQuery = true)
    long nextNumber();

    /**
     * Plant list with every filter optional. {@code invoiceId} keeps payments with an allocation to it; {@code search}
     * matches the payment code, the customer name or the reference. 🔴 Nullable String/date cast in {@code is null}.
     */
    @Query(value = """
            select p from CustomerPayment p
            where p.plantId = :plantId
              and (:status is null or p.status = :status)
              and (:method is null or p.method = :method)
              and (:customerId is null or p.customerId = :customerId)
              and (cast(:fromDate as date) is null or p.documentDate >= :fromDate)
              and (cast(:toDate as date) is null or p.documentDate <= :toDate)
              and (:invoiceId is null or exists (
                    select 1 from CustomerPaymentAllocation a where a.payment = p and a.invoiceId = :invoiceId))
              and (cast(:search as string) is null
                   or lower(p.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(p.customerName) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(p.reference, '')) like lower(concat('%', cast(:search as string), '%')))
            """,
            countQuery = """
            select count(p) from CustomerPayment p
            where p.plantId = :plantId
              and (:status is null or p.status = :status)
              and (:method is null or p.method = :method)
              and (:customerId is null or p.customerId = :customerId)
              and (cast(:fromDate as date) is null or p.documentDate >= :fromDate)
              and (cast(:toDate as date) is null or p.documentDate <= :toDate)
              and (:invoiceId is null or exists (
                    select 1 from CustomerPaymentAllocation a where a.payment = p and a.invoiceId = :invoiceId))
              and (cast(:search as string) is null
                   or lower(p.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(p.customerName) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(p.reference, '')) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<CustomerPayment> search(@Param("plantId") UUID plantId,
                                 @Param("status") CustomerPaymentStatus status,
                                 @Param("method") PaymentMethod method,
                                 @Param("customerId") UUID customerId,
                                 @Param("invoiceId") UUID invoiceId,
                                 @Param("fromDate") LocalDate fromDate,
                                 @Param("toDate") LocalDate toDate,
                                 @Param("search") String search,
                                 Pageable pageable);
}
