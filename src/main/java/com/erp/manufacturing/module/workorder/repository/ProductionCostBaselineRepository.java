package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionCostBaseline;
import com.erp.manufacturing.module.workorder.domain.ProductionCostBaselineMaterial;
import com.erp.manufacturing.module.workorder.domain.ProductionCostBaselineOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionCostBaselineRepository extends JpaRepository<ProductionCostBaseline, UUID> {

    Optional<ProductionCostBaseline> findByWorkOrderId(UUID workOrderId);

    boolean existsByWorkOrderId(UUID workOrderId);

    /** Materials and operations are two bags: read them in two queries, never one graph. */
    @Query("select m from ProductionCostBaselineMaterial m where m.baseline.baselineId = :baselineId order by m.lineNo")
    List<ProductionCostBaselineMaterial> findMaterials(@Param("baselineId") UUID baselineId);

    @Query("select o from ProductionCostBaselineOperation o where o.baseline.baselineId = :baselineId order by o.sequence")
    List<ProductionCostBaselineOperation> findOperations(@Param("baselineId") UUID baselineId);
}
