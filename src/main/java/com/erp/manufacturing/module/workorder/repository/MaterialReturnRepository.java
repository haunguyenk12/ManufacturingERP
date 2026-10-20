package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.MaterialReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MaterialReturnRepository extends JpaRepository<MaterialReturn, UUID> {

    /** Lines are read through {@link MaterialReturnLineRepository#findWithSourceByMaterialReturnIds}, one query for all. */
    @EntityGraph(attributePaths = {"workOrder"})
    Optional<MaterialReturn> findWithWorkOrderByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"workOrder"})
    Optional<MaterialReturn> findWithWorkOrderByMaterialReturnId(UUID materialReturnId);

    /** Resolves the work order of a return for the permission guard, without loading the document. */
    @Query("select r.workOrder.workOrderId from MaterialReturn r where r.materialReturnId = :materialReturnId")
    Optional<UUID> findWorkOrderIdByMaterialReturnId(@Param("materialReturnId") UUID materialReturnId);

    /**
     * Plant-scoped return history. {@code search} matches the return code or the work order number; the
     * {@code cast(... as string)} / {@code cast(... as timestamp)} on the {@code is null} sides are load-bearing
     * (CLAUDE.md §0.24, §0.36 #4). Lines are not fetched here (rule C13) — the service batches them.
     */
    @Query(value = """
            select r
            from MaterialReturn r
            join fetch r.workOrder w
            where w.plant.plantId = :plantId
              and (:workOrderId is null or w.workOrderId = :workOrderId)
              and (cast(:search as string) is null
                   or lower(r.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(w.workOrderNo) like lower(concat('%', cast(:search as string), '%')))
              and (cast(:postedFrom as timestamp) is null or r.postedAt >= :postedFrom)
              and (cast(:postedTo as timestamp) is null or r.postedAt < :postedTo)
            """,
            countQuery = """
            select count(r)
            from MaterialReturn r
            join r.workOrder w
            where w.plant.plantId = :plantId
              and (:workOrderId is null or w.workOrderId = :workOrderId)
              and (cast(:search as string) is null
                   or lower(r.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(w.workOrderNo) like lower(concat('%', cast(:search as string), '%')))
              and (cast(:postedFrom as timestamp) is null or r.postedAt >= :postedFrom)
              and (cast(:postedTo as timestamp) is null or r.postedAt < :postedTo)
            """)
    Page<MaterialReturn> findByPlant(@Param("plantId") UUID plantId,
                                     @Param("workOrderId") UUID workOrderId,
                                     @Param("search") String search,
                                     @Param("postedFrom") Instant postedFrom,
                                     @Param("postedTo") Instant postedTo,
                                     Pageable pageable);
}
