package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderOperationRepository extends JpaRepository<WorkOrderOperation, UUID> {

    List<WorkOrderOperation> findByWorkOrderWorkOrderIdOrderBySequenceAsc(UUID workOrderId);

    Optional<WorkOrderOperation> findByWorkOrderOperationIdAndWorkOrderWorkOrderId(UUID workOrderOperationId,
                                                                                  UUID workOrderId);
}
