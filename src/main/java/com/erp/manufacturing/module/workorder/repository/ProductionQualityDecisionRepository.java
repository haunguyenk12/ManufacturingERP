package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionQualityDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionQualityDecisionRepository extends JpaRepository<ProductionQualityDecision, UUID> {

    /** G10d: the child lots QC accepted out of a receipt's output — of one receipt, or of every receipt of one work order. */
    @Query("""
            select distinct d.acceptedLot.lotId from ProductionQualityDecision d
            where d.acceptedLot is not null
              and (:receiptId is null or d.receipt.receiptId = :receiptId)
              and (:workOrderId is null or d.receipt.workOrder.workOrderId = :workOrderId)
            """)
    List<UUID> findAcceptedLotIds(@Param("receiptId") UUID receiptId,
                                  @Param("workOrderId") UUID workOrderId);

    /** G9c: newest first, then id, so equal timestamps never shuffle between pages — same order as purchase QC. */
    @EntityGraph(attributePaths = {"acceptedLot", "rejectedLot", "acceptedMovement", "rejectedMovement"})
    @Query(value = """
            select d from ProductionQualityDecision d
            where d.receipt.receiptId = :receiptId
            order by d.decidedAt desc, d.decisionId desc
            """,
            countQuery = """
            select count(d) from ProductionQualityDecision d
            where d.receipt.receiptId = :receiptId
            """)
    Page<ProductionQualityDecision> findByReceipt(@Param("receiptId") UUID receiptId, Pageable pageable);

    @EntityGraph(attributePaths = {"receipt", "receiptLine", "acceptedLot", "rejectedLot", "acceptedMovement",
            "rejectedMovement"})
    Optional<ProductionQualityDecision> findWithDetailsByDecisionId(UUID decisionId);

    /** G11b: every QC decision on the receipts of a work order. */
    @Query("""
            select d from ProductionQualityDecision d
            left join fetch d.acceptedLot
            left join fetch d.rejectedLot
            where d.receipt.workOrder.workOrderId = :workOrderId
            """)
    List<ProductionQualityDecision> findByWorkOrder(@Param("workOrderId") UUID workOrderId);
}
