package com.erp.manufacturing.module.billing.repository;

import com.erp.manufacturing.module.billing.domain.SalesInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SalesInvoiceLineRepository extends JpaRepository<SalesInvoiceLine, UUID> {

    List<SalesInvoiceLine> findByInvoiceInvoiceIdOrderByLineNo(UUID invoiceId);

    List<SalesInvoiceLine> findByInvoiceInvoiceIdInOrderByLineNo(Collection<UUID> invoiceIds);

    /** Bulk delete, run at once, so a full replacement can reuse {@code line_no} 1..N (the trap of §0.40). */
    @Modifying(flushAutomatically = true)
    @Query("delete from SalesInvoiceLine l where l.invoice.invoiceId = :invoiceId")
    int deleteByInvoiceId(@Param("invoiceId") UUID invoiceId);

    /** B per delivery line: what POSTED invoices billed (one query, C14). */
    @Query("""
            select l.deliveryLineId as refId, sum(l.quantity) as quantity, sum(l.amount) as amount
            from SalesInvoiceLine l join l.invoice i
            where l.deliveryLineId in :deliveryLineIds
              and i.status = com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus.POSTED
            group by l.deliveryLineId
            """)
    List<BilledProjection> sumPostedByDeliveryLineIds(@Param("deliveryLineIds") Collection<UUID> deliveryLineIds);

    /** Ci per delivery line: what DRAFT invoices claim, the invoice being edited excluded by id. */
    @Query("""
            select l.deliveryLineId as refId, sum(l.quantity) as quantity, sum(l.amount) as amount
            from SalesInvoiceLine l join l.invoice i
            where l.deliveryLineId in :deliveryLineIds
              and i.status = com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus.DRAFT
              and (:excludeInvoiceId is null or i.invoiceId <> :excludeInvoiceId)
            group by l.deliveryLineId
            """)
    List<BilledProjection> sumDraftByDeliveryLineIds(@Param("deliveryLineIds") Collection<UUID> deliveryLineIds,
                                                     @Param("excludeInvoiceId") UUID excludeInvoiceId);

    /** The cumulative billing base per sales order line: quantity and whole-dong amount of POSTED invoices (FE R14). */
    @Query("""
            select l.salesOrderLineId as refId, sum(l.quantity) as quantity, sum(l.amount) as amount
            from SalesInvoiceLine l join l.invoice i
            where l.salesOrderLineId in :salesOrderLineIds
              and i.status = com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus.POSTED
            group by l.salesOrderLineId
            """)
    List<BilledProjection> sumPostedBySalesOrderLineIds(@Param("salesOrderLineIds") Collection<UUID> salesOrderLineIds);

    interface BilledProjection {
        UUID getRefId();

        BigDecimal getQuantity();

        BigDecimal getAmount();
    }
}
