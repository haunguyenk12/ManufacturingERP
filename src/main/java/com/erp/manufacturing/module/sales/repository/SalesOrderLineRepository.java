package com.erp.manufacturing.module.sales.repository;

import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.service.SalesOrderAllocationTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, UUID> {

    /**
     * All four eligibility conditions of spec §2.1 in a single aggregate query (rule {@code C14}):
     * order status is one of {@code CONFIRMED}/{@code IN_PRODUCTION}/{@code PARTIALLY_FULFILLED},
     * order belongs to {@code plantId}, {@code dueDate <= horizonEnd}, and open quantity is
     * strictly positive. Filtering any of these in Java would force one repository call per line.
     *
     * <p>The joins are explicit rather than path expressions ({@code l.salesOrder.status}) so the
     * generated SQL is unambiguous — the same class of mistake that caused the implicit INNER JOIN
     * bug fixed in {@code F1.6}. Here both associations are mandatory, so INNER JOIN is correct.
     */
    @Query("""
            select o.salesOrderId    as salesOrderId,
                   o.orderNo         as salesOrderCode,
                   l.salesOrderLineId as salesOrderLineId,
                   l.lineNo          as lineNo,
                   i.itemId          as itemId,
                   i.code            as itemSku,
                   i.name            as itemName,
                   i.unit            as uom,
                   (l.orderedQuantity - l.fulfilledQuantity) as openQuantity,
                   l.dueDate         as dueDate
            from SalesOrderLine l
            join l.salesOrder o
            join l.item i
            where o.plant.plantId = :plantId
              and o.status in :eligibleStatuses
              and l.dueDate <= :horizonEnd
              and l.orderedQuantity - l.fulfilledQuantity > 0
            order by l.dueDate asc, o.orderNo asc, l.lineNo asc
            """)
    List<SalesOrderPlanningDemandProjection> findEligiblePlanningDemands(
            @Param("plantId") UUID plantId,
            @Param("horizonEnd") LocalDate horizonEnd,
            @Param("eligibleStatuses") Collection<SalesOrderStatus> eligibleStatuses);

    /**
     * Resolves a batch of line ids into the flat view other modules are given (F6). One query for
     * however many allocations a work order — or a whole page of work orders — carries, per rule
     * {@code C14}. Both joins are on mandatory associations, so INNER JOIN is correct here.
     */
    @Query("""
            select new com.erp.manufacturing.module.sales.service.SalesOrderAllocationTarget(
                       l.salesOrderLineId, o.salesOrderId, o.orderNo, l.lineNo, l.dueDate,
                       i.unit, l.orderedQuantity, l.fulfilledQuantity)
            from SalesOrderLine l
            join l.salesOrder o
            join l.item i
            where l.salesOrderLineId in :salesOrderLineIds
            """)
    List<SalesOrderAllocationTarget> findAllocationTargets(
            @Param("salesOrderLineIds") Collection<UUID> salesOrderLineIds);

    @Query("""
            select distinct o.salesOrderId
            from SalesOrderLine l
            join l.salesOrder o
            where l.salesOrderLineId in :salesOrderLineIds
            """)
    List<UUID> findOrderIdsByLineIds(@Param("salesOrderLineIds") Collection<UUID> salesOrderLineIds);
}
