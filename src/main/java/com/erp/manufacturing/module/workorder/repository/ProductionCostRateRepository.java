package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionCostRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionCostRateRepository extends JpaRepository<ProductionCostRate, UUID> {

    Optional<ProductionCostRate> findTopByWorkCenterWorkCenterIdOrderByRateVersionDesc(UUID workCenterId);

    /** The current (highest version) rate of each work center that has one — one query for a page of centers (C14). */
    @Query("""
            select r
            from ProductionCostRate r
            where r.workCenter.workCenterId in :workCenterIds
              and r.rateVersion = (select max(r2.rateVersion) from ProductionCostRate r2
                                   where r2.workCenter = r.workCenter)
            """)
    List<ProductionCostRate> findCurrentByWorkCenterIds(@Param("workCenterIds") Collection<UUID> workCenterIds);

    Page<ProductionCostRate> findByWorkCenterWorkCenterId(UUID workCenterId, Pageable pageable);
}
