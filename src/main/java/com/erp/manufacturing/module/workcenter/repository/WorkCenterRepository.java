package com.erp.manufacturing.module.workcenter.repository;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WorkCenterRepository extends JpaRepository<WorkCenter, UUID> {

    boolean existsByPlantPlantIdAndCode(UUID plantId, String code);

    /**
     * No {@code cast(... as string)} here, unlike {@code UomRepository.search}: the "lower(bytea)"
     * hazard (CLAUDE.md §0.24) is specific to a {@code null} String parameter flowing into
     * {@code concat}/{@code like}, where Postgres has to infer a type for {@code ||} from the
     * parameter alone. {@code status} is compared with plain {@code =} against an enum-mapped
     * column, so Hibernate already binds it with the column's SQL type — same pattern as
     * {@code RoutingHeaderRepository.search}.
     */
    @Query("""
            select w
            from WorkCenter w
            where w.plant.plantId = :plantId
              and (:status is null or w.status = :status)
            """)
    Page<WorkCenter> search(@Param("plantId") UUID plantId,
                            @Param("status") OrganizationStatus status,
                            Pageable pageable);
}
