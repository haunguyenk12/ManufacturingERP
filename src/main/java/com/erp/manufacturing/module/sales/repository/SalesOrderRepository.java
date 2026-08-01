package com.erp.manufacturing.module.sales.repository;

import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {

    boolean existsByCompanyCompanyIdAndOrderNo(UUID companyId, String orderNo);

    @EntityGraph(attributePaths = {"company", "plant"})
    @Query("""
            select o
            from SalesOrder o
            where o.company.companyId = :companyId
              and o.plant.plantId = :plantId
              and (:status is null or o.status = :status)
            """)
    Page<SalesOrder> search(@Param("companyId") UUID companyId,
                            @Param("plantId") UUID plantId,
                            @Param("status") SalesOrderStatus status,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"company", "plant", "lines", "lines.item"})
    Optional<SalesOrder> findWithDetailsBySalesOrderId(UUID salesOrderId);

    /**
     * Loads whole orders with <b>all</b> of their lines (F6). The fulfilment roll-up decides
     * {@code PARTIALLY_FULFILLED} vs {@code FULFILLED} by looking at every line, so filtering the
     * collection down to the affected lines would silently report a partly-shipped order as
     * complete.
     */
    @EntityGraph(attributePaths = {"lines"})
    List<SalesOrder> findBySalesOrderIdIn(Collection<UUID> salesOrderIds);
}
