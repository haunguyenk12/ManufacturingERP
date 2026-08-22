package com.erp.manufacturing.module.dataimport.repository;

import com.erp.manufacturing.module.dataimport.domain.ImportProfile;
import com.erp.manufacturing.module.dataimport.domain.ImportProfileStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ImportProfileRepository extends JpaRepository<ImportProfile, UUID> {

    boolean existsByCode(String code);

    /**
     * Profiles a company may pick from: its own, plus the shared ones ({@code company_id IS NULL}).
     *
     * <p>Every filter is nullable and compared with the {@code :param IS NULL OR ...} idiom already
     * used across this repository. None of the parameters need a {@code cast(... as ...)}: they all
     * appear a second time in a real comparison, which is what gives Postgres the type it needs
     * (see {@code common/audit/CLAUDE.md} §9.12 for the case where that is not true).
     */
    @Query("""
            select p from ImportProfile p
            where (:targetType is null or p.targetType = :targetType)
              and (:status is null or p.status = :status)
              and (p.company is null or p.company.companyId = :companyId)
            """)
    Page<ImportProfile> search(@Param("companyId") UUID companyId,
                               @Param("targetType") ImportTargetType targetType,
                               @Param("status") ImportProfileStatus status,
                               Pageable pageable);
}
