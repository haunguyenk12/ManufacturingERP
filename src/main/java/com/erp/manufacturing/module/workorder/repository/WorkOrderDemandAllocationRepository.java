package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WorkOrderDemandAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkOrderDemandAllocationRepository extends JpaRepository<WorkOrderDemandAllocation, UUID> {

    /**
     * Every allocation of a batch of work orders in one query. A page of work orders resolves its
     * {@code allocations[]} through this, never one call per row (rule {@code C14}).
     */
    List<WorkOrderDemandAllocation> findByWorkOrderWorkOrderIdIn(Collection<UUID> workOrderIds);
}
