package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.PlanningDemand;
import com.erp.manufacturing.module.planning.domain.PlanningDemandStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanningDemandRepository extends JpaRepository<PlanningDemand, UUID> {

    @EntityGraph(attributePaths = {"company", "plant", "item", "warehouse"})
    Optional<PlanningDemand> findWithDetailsByPlanningDemandId(UUID planningDemandId);

    @EntityGraph(attributePaths = {"company", "plant", "item", "warehouse"})
    @Query("""
            select d
            from PlanningDemand d
            where d.company.companyId = :companyId
              and d.plant.plantId = :plantId
              and (:warehouseId is null or d.warehouse.warehouseId = :warehouseId)
              and (:itemId is null or d.item.itemId = :itemId)
              and (:status is null or d.status = :status)
            """)
    Page<PlanningDemand> search(@Param("companyId") UUID companyId,
                                @Param("plantId") UUID plantId,
                                @Param("warehouseId") UUID warehouseId,
                                @Param("itemId") UUID itemId,
                                @Param("status") PlanningDemandStatus status,
                                Pageable pageable);

    @EntityGraph(attributePaths = {"company", "plant", "item", "warehouse"})
    @Query("""
            select d
            from PlanningDemand d
            where d.company.companyId = :companyId
              and d.plant.plantId = :plantId
              and (:warehouseId is null or d.warehouse is null or d.warehouse.warehouseId = :warehouseId)
              and d.status = com.erp.manufacturing.module.planning.domain.PlanningDemandStatus.OPEN
              and d.dueDate between :horizonStart and :horizonEnd
            order by d.priority asc, d.dueDate asc, d.createdAt asc
            """)
    List<PlanningDemand> findOpenDemandsForRun(@Param("companyId") UUID companyId,
                                               @Param("plantId") UUID plantId,
                                               @Param("warehouseId") UUID warehouseId,
                                               @Param("horizonStart") LocalDate horizonStart,
                                               @Param("horizonEnd") LocalDate horizonEnd);
}
