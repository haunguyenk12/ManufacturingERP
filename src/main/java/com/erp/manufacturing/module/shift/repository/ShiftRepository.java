package com.erp.manufacturing.module.shift.repository;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.shift.domain.Shift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    boolean existsByPlantPlantIdAndCode(UUID plantId, String code);

    /**
     * No {@code cast(... as string)} needed — {@code status} is compared with plain {@code =}
     * against an enum-mapped column (same reasoning as {@code WorkCenterRepository.search}, the
     * "lower(bytea)" hazard from CLAUDE.md §0.24 only applies to nullable String parameters flowing
     * into {@code concat}/{@code like}).
     */
    @Query("""
            select s
            from Shift s
            where s.plant.plantId = :plantId
              and (:status is null or s.status = :status)
            """)
    Page<Shift> search(@Param("plantId") UUID plantId,
                        @Param("status") OrganizationStatus status,
                        Pageable pageable);
}
