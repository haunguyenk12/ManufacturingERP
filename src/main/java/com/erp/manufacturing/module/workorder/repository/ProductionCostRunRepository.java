package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionCostRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductionCostRunRepository extends JpaRepository<ProductionCostRun, UUID> {

    /** Newest first; the id breaks a tie on the same microsecond (FE §3.4 stable sort). */
    @Query("select r from ProductionCostRun r where r.workOrderId = :workOrderId order by r.createdAt desc, r.runId desc")
    Page<ProductionCostRun> findByWorkOrder(@Param("workOrderId") UUID workOrderId, Pageable pageable);

    Optional<ProductionCostRun> findByRunIdAndWorkOrderId(UUID runId, UUID workOrderId);

    Optional<ProductionCostRun> findByWorkOrderIdAndState(UUID workOrderId, String state);
}
