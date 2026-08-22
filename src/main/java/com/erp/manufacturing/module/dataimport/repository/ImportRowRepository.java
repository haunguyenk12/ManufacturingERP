package com.erp.manufacturing.module.dataimport.repository;

import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ImportRowRepository extends JpaRepository<ImportRow, UUID> {

    List<ImportRow> findByImportRunImportRunIdOrderByRowNumberAsc(UUID importRunId);

    List<ImportRow> findByImportRunImportRunIdAndStatusOrderByRowNumberAsc(UUID importRunId,
                                                                          ImportRowStatus status);

    @Query("""
            select r from ImportRow r
            where r.importRun.importRunId = :importRunId
              and (:status is null or r.status = :status)
            """)
    Page<ImportRow> search(@Param("importRunId") UUID importRunId,
                           @Param("status") ImportRowStatus status,
                           Pageable pageable);

    void deleteByImportRunImportRunId(UUID importRunId);
}
