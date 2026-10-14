package com.erp.manufacturing.module.dataimport.repository;

import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Both import paths share {@code import_runs}. Every query below names the {@link ImportRunKind} it
 * serves, so a fixed-template batch is invisible to {@code /v1/import-runs} and vice versa — without
 * that, the old path's {@code cancel} could cancel a {@code READY} batch and its list would show
 * batches of other owners.
 */
public interface ImportRunRepository extends JpaRepository<ImportRun, UUID> {

    /**
     * Replay lookup for the write step (apply on the old path, commit on the new one). Its scope must
     * stay identical to {@code uk_import_runs_idempotency_key}: narrower and a replay returns a
     * non-deterministic row, wider and the database rejects a legitimate run (invariant B69's lesson,
     * restated for B117).
     */
    @EntityGraph(attributePaths = {"company", "profile", "plant", "warehouse"})
    Optional<ImportRun> findByIdempotencyKey(String idempotencyKey);

    /**
     * Detail fetch of a <em>mapped-profile</em> run. Only {@code @ManyToOne} paths are joined and none
     * is a collection — adding the run's rows here would be a second bag on the same graph, which this
     * repository has met three times already (CLAUDE.md §0.27, §0.29, §0.45). Rows are read through
     * {@link ImportRowRepository}, paged.
     */
    @EntityGraph(attributePaths = {"company", "profile", "plant", "warehouse"})
    @Query("""
            select r from ImportRun r
            where r.importRunId = :importRunId
              and r.kind = com.erp.manufacturing.module.dataimport.domain.ImportRunKind.MAPPED_PROFILE
            """)
    Optional<ImportRun> findWithDetailsByImportRunId(@Param("importRunId") UUID importRunId);

    boolean existsByImportRunIdAndKind(UUID importRunId, ImportRunKind kind);

    /** List of the mapped-profile path ({@code /v1/import-runs}), per company. */
    @Query("""
            select r from ImportRun r
            where r.company.companyId = :companyId
              and r.kind = com.erp.manufacturing.module.dataimport.domain.ImportRunKind.MAPPED_PROFILE
              and (:targetType is null or r.targetType = :targetType)
              and (:status is null or r.status = :status)
            """)
    Page<ImportRun> search(@Param("companyId") UUID companyId,
                           @Param("targetType") ImportTargetType targetType,
                           @Param("status") ImportRunStatus status,
                           Pageable pageable);

    // ── Fixed-template batches (G3) ────────────────────────────────────────

    @EntityGraph(attributePaths = {"company", "plant"})
    Optional<ImportRun> findByImportRunIdAndKind(UUID importRunId, ImportRunKind kind);

    /** Upload replay. Scope matches {@code uk_import_runs_owner_upload_key} exactly. */
    @EntityGraph(attributePaths = {"company", "plant"})
    Optional<ImportRun> findByOwnerUserIdAndUploadIdempotencyKey(UUID ownerUserId, String uploadIdempotencyKey);

    /**
     * Serialises commits of the same batch: the second of two concurrent commits waits here, then
     * finds the batch {@code COMMITTED} and answers with the receipt instead of creating the rows again
     * (FE A16).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from ImportRun r
            where r.importRunId = :importRunId
              and r.kind = com.erp.manufacturing.module.dataimport.domain.ImportRunKind.FIXED_TEMPLATE
            """)
    Optional<ImportRun> lockFixedTemplateBatch(@Param("importRunId") UUID importRunId);

    /**
     * History of one owner in one company (FE Q13), limited to the catalogues the caller may still use.
     *
     * <p>{@code left join r.plant} is load-bearing: a supplier/customer batch has no plant, and the
     * implicit inner join that {@code r.plant.plantId} would produce drops every one of them from the
     * page. {@code cast(:search as string)} is load-bearing too (the {@code lower(bytea)} trap,
     * CLAUDE.md §0.24). {@code DataImportBatchRepositoryIT} keeps both.
     */
    @Query("""
            select r from ImportRun r
            left join r.plant p
            where r.kind = com.erp.manufacturing.module.dataimport.domain.ImportRunKind.FIXED_TEMPLATE
              and r.ownerUserId = :ownerUserId
              and r.company.companyId = :companyId
              and ((:includeItems = true
                    and r.targetType = com.erp.manufacturing.module.dataimport.domain.ImportTargetType.ITEM
                    and p.plantId = :plantId)
                or (:includeSuppliers = true
                    and r.targetType = com.erp.manufacturing.module.dataimport.domain.ImportTargetType.SUPPLIER)
                or (:includeCustomers = true
                    and r.targetType = com.erp.manufacturing.module.dataimport.domain.ImportTargetType.CUSTOMER))
              and (:status is null or r.status = :status)
              and (cast(:search as string) is null
                   or lower(r.originalFilename) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<ImportRun> searchBatches(@Param("ownerUserId") UUID ownerUserId,
                                  @Param("companyId") UUID companyId,
                                  @Param("plantId") UUID plantId,
                                  @Param("includeItems") boolean includeItems,
                                  @Param("includeSuppliers") boolean includeSuppliers,
                                  @Param("includeCustomers") boolean includeCustomers,
                                  @Param("status") ImportRunStatus status,
                                  @Param("search") String search,
                                  Pageable pageable);
}
