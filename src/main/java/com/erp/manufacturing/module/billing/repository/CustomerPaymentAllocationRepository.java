package com.erp.manufacturing.module.billing.repository;

import com.erp.manufacturing.module.billing.domain.CustomerPaymentAllocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CustomerPaymentAllocationRepository extends JpaRepository<CustomerPaymentAllocation, UUID> {

    List<CustomerPaymentAllocation> findByPaymentPaymentIdOrderByLineNo(UUID paymentId);

    List<CustomerPaymentAllocation> findByPaymentPaymentIdInOrderByLineNo(Collection<UUID> paymentIds);

    /** Bulk delete, run at once, so a full replacement can reuse {@code line_no} 1..N (the trap of §0.40). */
    @Modifying(flushAutomatically = true)
    @Query("delete from CustomerPaymentAllocation a where a.payment.paymentId = :paymentId")
    int deleteByPaymentId(@Param("paymentId") UUID paymentId);

    /** P per invoice: what POSTED payments allocated (one query, C14). */
    @Query("""
            select a.invoiceId as invoiceId, sum(a.amount) as amount
            from CustomerPaymentAllocation a join a.payment p
            where a.invoiceId in :invoiceIds
              and p.status = com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus.POSTED
            group by a.invoiceId
            """)
    List<InvoiceAmountProjection> sumPostedByInvoiceIds(@Param("invoiceIds") Collection<UUID> invoiceIds);

    /** Cp per invoice: what DRAFT payments claim, the payment being edited excluded by id. */
    @Query("""
            select a.invoiceId as invoiceId, sum(a.amount) as amount
            from CustomerPaymentAllocation a join a.payment p
            where a.invoiceId in :invoiceIds
              and p.status = com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus.DRAFT
              and (:excludePaymentId is null or p.paymentId <> :excludePaymentId)
            group by a.invoiceId
            """)
    List<InvoiceAmountProjection> sumDraftByInvoiceIds(@Param("invoiceIds") Collection<UUID> invoiceIds,
                                                       @Param("excludePaymentId") UUID excludePaymentId);

    /** The payments allocated to one invoice (FE "relatedPayments"), newest first, every status. */
    @Query(value = """
            select a from CustomerPaymentAllocation a join fetch a.payment p
            where a.invoiceId = :invoiceId
            order by p.documentDate desc, p.code desc
            """,
            countQuery = "select count(a) from CustomerPaymentAllocation a where a.invoiceId = :invoiceId")
    Page<CustomerPaymentAllocation> findByInvoiceId(@Param("invoiceId") UUID invoiceId, Pageable pageable);

    interface InvoiceAmountProjection {
        UUID getInvoiceId();

        BigDecimal getAmount();
    }
}
