package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WorkOrderCostAccumulator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkOrderCostAccumulatorRepository extends JpaRepository<WorkOrderCostAccumulator, UUID> {

    Optional<WorkOrderCostAccumulator> findByWorkOrderWorkOrderId(UUID workOrderId);
}
