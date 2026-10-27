package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionCostAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductionCostAdjustmentRepository extends JpaRepository<ProductionCostAdjustment, UUID> {

    Page<ProductionCostAdjustment> findByRunIdOrderByLineNo(UUID runId, Pageable pageable);

    List<ProductionCostAdjustment> findByDeliveryLineIdIn(Collection<UUID> deliveryLineIds);

    /** Stock entries (no delivery line) of these lots — the value left on hand at close. */
    List<ProductionCostAdjustment> findByLotIdInAndDeliveryLineIdIsNull(Collection<UUID> lotIds);
}
