package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionReceiptLine;
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

    @Query("""
            select coalesce(sum(l.quantity), 0)
            from ProductionReceiptLine l
            where l.receipt.workOrder.workOrderId = :workOrderId
              and l.receipt.status = com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.POSTED
            """)
    BigDecimal sumReceivedQuantityByWorkOrder(@Param("workOrderId") UUID workOrderId);
}
