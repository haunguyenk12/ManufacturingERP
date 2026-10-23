package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionReceiptContribution;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductionReceiptContributionRepository extends JpaRepository<ProductionReceiptContribution, UUID> {

    /**
     * G9b: what each final-good execution of a work order is already claimed for, by receipts that still claim output
     * (DRAFT, PENDING_APPROVAL, APPROVED). {@code excludeReceiptId} leaves one receipt out — the draft being edited.
     */
    @Query("""
            select c.execution.productionExecutionId as refId, coalesce(sum(c.quantity), 0) as quantity
            from ProductionReceiptContribution c
            where c.receipt.workOrder.workOrderId = :workOrderId
              and c.receipt.status in (
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.DRAFT,
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.PENDING_APPROVAL,
                    com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED)
              and (:excludeReceiptId is null or c.receipt.receiptId <> :excludeReceiptId)
            group by c.execution.productionExecutionId
            """)
    List<KeyedQuantityProjection> sumLiveByExecution(@Param("workOrderId") UUID workOrderId,
                                                     @Param("excludeReceiptId") UUID excludeReceiptId);

    /** The contributions of a page of receipts, executions included — one query for the page (C14). */
    @EntityGraph(attributePaths = {"execution", "execution.operation"})
    List<ProductionReceiptContribution> findByReceiptReceiptIdIn(Collection<UUID> receiptIds);

    /** A draft edit allocates again from scratch. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from ProductionReceiptContribution c where c.receipt.receiptId = :receiptId")
    int deleteByReceiptId(@Param("receiptId") UUID receiptId);
}
