package com.erp.manufacturing.module.dataimport.repository;

import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ImportRunRepository extends JpaRepository<ImportRun, UUID> {

    /**
     * Replay lookup for the apply step. Its scope must stay identical to
     * {@code uk_import_runs_idempotency_key}: narrower and a replay returns a non-deterministic row,
     * wider and the database rejects a legitimate run (invariant B69's lesson, restated for B117).
     */
    @EntityGraph(attributePaths = {"company", "profile", "plant", "warehouse"})
    Optional<ImportRun> findByIdempotencyKey(String idempotencyKey);

    /**
     * Detail fetch. Only two {@code @ManyToOne} paths are joined and neither is a collection — adding
     * the run's rows here would be a second bag on the same graph, which this repository has met
     * three times already (CLAUDE.md §0.27, §0.29, §0.45). Rows are read through
     * {@link ImportRowRepository}, paged.
     */
    @EntityGraph(attributePaths = {"company", "profile", "plant", "warehouse"})
    Optional<ImportRun> findWithDetailsByImportRunId(UUID importRunId);

    @Query("""
            select r from ImportRun r
            where r.company.companyId = :companyId
              and (:targetType is null or r.targetType = :targetType)
              and (:status is null or r.status = :status)
            """)
    Page<ImportRun> search(@Param("companyId") UUID companyId,
                           @Param("targetType") ImportTargetType targetType,
                           @Param("status") ImportRunStatus status,
                           Pageable pageable);
}
