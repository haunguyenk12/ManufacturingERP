package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlantRepository extends JpaRepository<Plant, UUID> {

    boolean existsByCompanyCompanyIdAndCode(UUID companyId, String code);

    List<Plant> findByCompanyCompanyIdAndCodeIn(UUID companyId, Collection<String> codes);

    Page<Plant> findByCompanyCompanyId(UUID companyId, Pageable pageable);

    /**
     * G2-4: the list FE filters by code/name and status. {@code cast(:search as string)} is load-bearing
     * (CLAUDE.md §0.24): without it a {@code null} search is bound untyped and Postgres rejects the
     * whole statement with {@code function lower(bytea) does not exist}. {@code OrganizationSearchRepositoryIT}
     * keeps it there.
     */
    @Query("""
            select p
            from Plant p
            where p.company.companyId = :companyId
              and (:status is null or p.status = :status)
              and (cast(:search as string) is null
                   or lower(p.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(p.name) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<Plant> search(@Param("companyId") UUID companyId,
                       @Param("status") OrganizationStatus status,
                       @Param("search") String search,
                       Pageable pageable);
}
