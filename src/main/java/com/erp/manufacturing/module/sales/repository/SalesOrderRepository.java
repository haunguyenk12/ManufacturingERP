package com.erp.manufacturing.module.sales.repository;

import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
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

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {

    boolean existsByCompanyCompanyIdAndOrderNo(UUID companyId, String orderNo);

    /**
     * G6a list (FE R03): text search over order number, customer name/code and customer reference;
     * order-date range; due-date range applied to the order's <b>earliest</b> line due date (FE Q3).
     * Every filter is optional.
     *
     * <p>🔴 The {@code cast(... as …)} on the {@code is null} side of every optional parameter is
     * load-bearing: Hibernate binds one positional parameter per occurrence, so the {@code is null}
     * occurrence has no type context and Postgres rejects it ({@code lower(bytea)} for strings,
     * "could not determine data type" for dates — CLAUDE.md §0.24, §0.36 #4).
     */
    @EntityGraph(attributePaths = {"company", "plant"})
    @Query("""
            select o
            from SalesOrder o
            where o.company.companyId = :companyId
              and o.plant.plantId = :plantId
              and (:status is null or o.status = :status)
              and (cast(:search as string) is null
                   or lower(o.orderNo) like lower(concat('%', cast(:search as string), '%'))
                   or lower(o.customerName) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(o.customerCode, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(o.customerReference, '')) like lower(concat('%', cast(:search as string), '%')))
              and (cast(:orderDateFrom as date) is null or o.orderDate >= :orderDateFrom)
              and (cast(:orderDateTo as date) is null or o.orderDate <= :orderDateTo)
              and (cast(:dueDateFrom as date) is null
                   or (select min(l.dueDate) from SalesOrderLine l where l.salesOrder = o) >= :dueDateFrom)
              and (cast(:dueDateTo as date) is null
                   or (select min(l.dueDate) from SalesOrderLine l where l.salesOrder = o) <= :dueDateTo)
            """)
    Page<SalesOrder> searchExtended(@Param("companyId") UUID companyId,
                                    @Param("plantId") UUID plantId,
                                    @Param("status") SalesOrderStatus status,
                                    @Param("search") String search,
                                    @Param("orderDateFrom") LocalDate orderDateFrom,
                                    @Param("orderDateTo") LocalDate orderDateTo,
                                    @Param("dueDateFrom") LocalDate dueDateFrom,
                                    @Param("dueDateTo") LocalDate dueDateTo,
                                    Pageable pageable);

    /** Next value of {@code sales_order_number_seq} (V74) — concurrent drafts never share a number. */
    @Query(value = "select nextval('sales_order_number_seq')", nativeQuery = true)
    long nextOrderNumber();

    /**
     * G10b: row lock only, by id — the caller re-reads the order afterwards
     * ({@code SalesOrderCommercialLookupService.lockOrder}, B194).
     */
    @Query(value = "select sales_order_id from sales_orders where sales_order_id = :salesOrderId for update",
            nativeQuery = true)
    Optional<UUID> lockRowBySalesOrderId(@Param("salesOrderId") UUID salesOrderId);

    @EntityGraph(attributePaths = {"company", "plant", "lines", "lines.item"})
    Optional<SalesOrder> findWithDetailsBySalesOrderId(UUID salesOrderId);

    /** G10b: the orders behind a page of documents with their lines and items, one query (C14). */
    @EntityGraph(attributePaths = {"lines", "lines.item"})
    List<SalesOrder> findWithLinesBySalesOrderIdIn(java.util.Collection<UUID> salesOrderIds);

    /**
     * Loads whole orders with <b>all</b> of their lines (F6). The fulfilment roll-up decides
     * {@code PARTIALLY_FULFILLED} vs {@code FULFILLED} by looking at every line, so filtering the
     * collection down to the affected lines would silently report a partly-shipped order as
     * complete.
     */
    @EntityGraph(attributePaths = {"lines"})
    List<SalesOrder> findBySalesOrderIdIn(Collection<UUID> salesOrderIds);
}
