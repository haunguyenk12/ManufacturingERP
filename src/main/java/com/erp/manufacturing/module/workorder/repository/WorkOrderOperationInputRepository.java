package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WorkOrderOperationInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkOrderOperationInputRepository extends JpaRepository<WorkOrderOperationInput, UUID> {

    /** Confirmed input per operation of one work order (G8c). */
    @Query("""
            select i.operation.workOrderOperationId as refId, coalesce(sum(i.quantity), 0) as quantity
            from WorkOrderOperationInput i
            where i.workOrder.workOrderId = :workOrderId
            group by i.operation.workOrderOperationId
            """)
    List<KeyedQuantityProjection> sumInputByOperation(@Param("workOrderId") UUID workOrderId);

    /** Good output already transferred out of each operation to its successor (G8c). */
    @Query("""
            select i.sourceOperationId as refId, coalesce(sum(i.quantity), 0) as quantity
            from WorkOrderOperationInput i
            where i.workOrder.workOrderId = :workOrderId
              and i.sourceOperationId is not null
            group by i.sourceOperationId
            """)
    List<KeyedQuantityProjection> sumTransferredBySourceOperation(@Param("workOrderId") UUID workOrderId);
}
