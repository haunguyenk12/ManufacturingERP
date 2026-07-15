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
