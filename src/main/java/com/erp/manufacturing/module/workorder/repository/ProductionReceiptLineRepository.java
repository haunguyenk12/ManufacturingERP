package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionReceiptLine;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductionReceiptLineRepository extends JpaRepository<ProductionReceiptLine, UUID> {

    @EntityGraph(attributePaths = {"receipt", "item", "warehouse", "lot", "stockMovement"})
    List<ProductionReceiptLine> findByReceiptReceiptIdIn(Collection<UUID> receiptIds);

    /**
     * G9c: whether a lot is the output lot of an APPROVED production receipt line QC has not finished judging —
     * used by {@code LotQcOriginLookupService} (C2-2) so the generic status-change endpoint cannot release it past
     * QC. A line fully judged (whole-lot or partial) frees the lot, so one quarantined again by hand stays releasable.
     */
    @Query("""
            select count(l) > 0 from ProductionReceiptLine l
            where l.lot.lotId = :lotId
              and l.receipt.status = :approved
              and l.quantity > l.qcAcceptedQuantity + l.qcRejectedQuantity
            """)
    boolean existsPendingQualityForLot(@Param("lotId") UUID lotId,
                                       @Param("approved") ProductionReceiptStatus approved);

    @Query("""
            select coalesce(sum(l.quantity), 0)
            from ProductionReceiptLine l
            where l.receipt.workOrder.workOrderId = :workOrderId
              and l.receipt.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.APPROVED
            """)
    BigDecimal sumReceivedQuantityByWorkOrder(@Param("workOrderId") UUID workOrderId);
}
