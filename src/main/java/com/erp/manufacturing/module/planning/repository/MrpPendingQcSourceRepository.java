package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.MrpPendingQcSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MrpPendingQcSourceRepository extends JpaRepository<MrpPendingQcSource, UUID> {

    /**
     * CF-4: the pending-QC sources a run captured, optionally narrowed to one requirement line and/or warehouse.
     * Stable order: receipt code, then line id. Nullable UUID filters only appear next to a typed comparison, so no
     * cast is needed (§0.36 #4 concerns parameters that appear only in {@code IS NULL}).
     */
    @Query("""
            select s from MrpPendingQcSource s
            where s.mrpRunId = :runId
              and (:requirementLineId is null or s.requirementLineId = :requirementLineId)
              and (:warehouseId is null or s.warehouseId = :warehouseId)
            order by s.receiptCode asc, s.goodsReceiptLineId asc
            """)
    Page<MrpPendingQcSource> search(@Param("runId") UUID runId,
                                    @Param("requirementLineId") UUID requirementLineId,
                                    @Param("warehouseId") UUID warehouseId,
                                    Pageable pageable);
}
