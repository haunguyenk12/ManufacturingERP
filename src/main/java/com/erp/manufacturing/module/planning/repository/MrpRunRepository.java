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

    /**
     * Replay lookup for {@code POST /planning-runs} (V58). The scope here must stay in step with
     * {@code uk_mrp_runs_idempotency_key}: whole-table, because one key identifies one run attempt —
     * unlike {@code stock_movements}, where the same key legitimately means different documents in
     * different operations and the constraint is therefore {@code (key, movement_type)} (B69).
     * <p>
     * Fetches the associations the response mapper reads, so replaying does not lazy-load three
     * extra rows.
     */
    @EntityGraph(attributePaths = {"company", "plant", "warehouse"})
    Optional<MrpRun> findByIdempotencyKey(String idempotencyKey);

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
