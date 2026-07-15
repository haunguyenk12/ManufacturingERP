package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WipTransaction;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface WipTransactionRepository extends JpaRepository<WipTransaction, UUID> {

    Page<WipTransaction> findByWorkOrderWorkOrderId(UUID workOrderId, Pageable pageable);

    @Query("""
            select coalesce(sum(t.quantity), 0)
            from WipTransaction t
            where t.workOrder.workOrderId = :workOrderId
              and t.transactionType = :transactionType
            """)
    BigDecimal sumQuantityByWorkOrderAndType(@Param("workOrderId") UUID workOrderId,
                                             @Param("transactionType") WipTransactionType transactionType);
}
