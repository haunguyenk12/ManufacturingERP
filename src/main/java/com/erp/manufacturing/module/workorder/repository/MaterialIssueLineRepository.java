package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.MaterialIssueLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MaterialIssueLineRepository extends JpaRepository<MaterialIssueLine, UUID> {

    @EntityGraph(attributePaths = {"issue", "componentLine", "reservation", "item", "warehouse", "lot", "stockMovement"})
    List<MaterialIssueLine> findByIssueIssueIdIn(Collection<UUID> issueIds);

    /**
     * G8c: the issue lines an execution says it consumed, with their issue — so the caller can check each belongs to
     * the work order and was posted, in one query for the whole batch (C14).
     */
    @Query("""
            select l from MaterialIssueLine l
            join fetch l.issue i
            where l.issueLineId in :issueLineIds
            """)
    List<MaterialIssueLine> findWithIssueByIssueLineIdIn(@Param("issueLineIds") Collection<UUID> issueLineIds);

    @Query("""
            select l.componentLine.componentLineId as componentLineId,
                   coalesce(sum(l.quantity), 0) as quantity
            from MaterialIssueLine l
            where l.issue.workOrder.workOrderId = :workOrderId
              and l.issue.status = com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus.POSTED
            group by l.componentLine.componentLineId
            """)
    List<ComponentQuantityProjection> sumIssuedByWorkOrder(@Param("workOrderId") UUID workOrderId);

    @Query("""
            select coalesce(sum(l.quantity), 0)
            from MaterialIssueLine l
            where l.issue.workOrder.workOrderId = :workOrderId
              and l.issue.status = com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus.POSTED
            """)
    BigDecimal sumIssuedQuantityByWorkOrder(@Param("workOrderId") UUID workOrderId);
}
