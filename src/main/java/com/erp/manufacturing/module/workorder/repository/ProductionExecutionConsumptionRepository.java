package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionExecutionConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductionExecutionConsumptionRepository extends JpaRepository<ProductionExecutionConsumption, UUID> {

    /** Material already recorded as used, per issue line — one query for the whole batch (C14). */
    @Query("""
            select c.issueLineId as refId, coalesce(sum(c.quantity), 0) as quantity
            from ProductionExecutionConsumption c
            where c.issueLineId in :issueLineIds
            group by c.issueLineId
            """)
    List<KeyedQuantityProjection> sumByIssueLineIds(@Param("issueLineIds") Collection<UUID> issueLineIds);

    List<ProductionExecutionConsumption> findByProductionExecutionProductionExecutionIdIn(Collection<UUID> executionIds);
}
