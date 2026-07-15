package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.MrpRun;
import com.erp.manufacturing.module.planning.domain.MrpRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MrpRunRepository extends JpaRepository<MrpRun, UUID> {

    @EntityGraph(attributePaths = {"company", "plant", "warehouse"})
    Optional<MrpRun> findWithDetailsByMrpRunId(UUID mrpRunId);

    @EntityGraph(attributePaths = {"company", "plant", "warehouse"})
    @Query("""
            select r
            from MrpRun r
            where r.company.companyId = :companyId
              and r.plant.plantId = :plantId
              and (:warehouseId is null or r.warehouse.warehouseId = :warehouseId)
              and (:status is null or r.status = :status)
            """)
    Page<MrpRun> search(@Param("companyId") UUID companyId,
                        @Param("plantId") UUID plantId,
                        @Param("warehouseId") UUID warehouseId,
                        @Param("status") MrpRunStatus status,
                        Pageable pageable);
}
