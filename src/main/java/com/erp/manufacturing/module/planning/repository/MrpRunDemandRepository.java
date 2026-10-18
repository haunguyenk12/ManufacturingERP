package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.MrpRunDemand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MrpRunDemandRepository extends JpaRepository<MrpRunDemand, UUID> {

    /** Sales order line ids a run planned (the reference of its SALES_ORDER_LINE demands), G6b. */
    @Query("""
            select d.planningDemand.referenceId
            from MrpRunDemand d
            where d.mrpRun.mrpRunId = :mrpRunId
              and d.planningDemand.referenceType = :referenceType
            """)
    List<String> findDemandReferenceIds(@Param("mrpRunId") UUID mrpRunId,
                                        @Param("referenceType") String referenceType);
}
