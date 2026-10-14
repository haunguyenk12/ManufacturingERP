package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    boolean existsByPlantPlantIdAndCode(UUID plantId, String code);

    List<Warehouse> findByPlantPlantIdAndCodeIn(UUID plantId, Collection<String> codes);

    Page<Warehouse> findByPlantPlantId(UUID plantId, Pageable pageable);

    /**
     * G2-4: the list FE filters by code/name and status. {@code cast(:search as string)} is load-bearing
     * (CLAUDE.md §0.24): without it a {@code null} search is bound untyped and Postgres rejects the
     * whole statement with {@code function lower(bytea) does not exist}. {@code OrganizationSearchRepositoryIT}
     * keeps it there.
     */
    @Query("""
            select w
            from Warehouse w
            where w.plant.plantId = :plantId
              and (:status is null or w.status = :status)
              and (cast(:search as string) is null
                   or lower(w.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(w.name) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<Warehouse> search(@Param("plantId") UUID plantId,
                           @Param("status") OrganizationStatus status,
                           @Param("search") String search,
                           Pageable pageable);

    /** G2-4: a plant with an ACTIVE warehouse cannot be deactivated (NEXT_PHASE_PLAN.md 9.2 D4). */
    boolean existsByPlantPlantIdAndStatus(UUID plantId, OrganizationStatus status);

    List<Warehouse> findByPlantPlantId(UUID plantId);

    List<Warehouse> findByPlantCompanyCompanyId(UUID companyId);
}
