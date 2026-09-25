package com.erp.manufacturing.module.bom.repository;

import com.erp.manufacturing.module.bom.domain.BomLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BomLineRepository extends JpaRepository<BomLine, UUID> {

    /**
     * All lines of one BOM, used by {@code BomLineAuditProvider} to snapshot the collection before a
     * line command runs. The generic audit snapshot only covers singular attributes, so without this
     * a BOM whose lines changed reports zero field changes — the audit detail claiming the BOM was
     * untouched is worse than having no detail at all.
     */
    List<BomLine> findByBomBomIdOrderByLineNoAsc(UUID bomId);

    boolean existsByBomBomIdAndComponentItemItemId(UUID bomId, UUID componentItemId);

    boolean existsByBomBomIdAndLineNo(UUID bomId, Integer lineNo);

    @Query("""
            select count(l) > 0
            from BomLine l
            where l.bom.bomId = :bomId
              and l.componentItem.itemId = :componentItemId
              and l.lineId <> :lineId
            """)
    boolean existsDuplicateComponent(@Param("bomId") UUID bomId,
                                     @Param("componentItemId") UUID componentItemId,
                                     @Param("lineId") UUID lineId);

    @Query("""
            select count(l) > 0
            from BomLine l
            where l.bom.bomId = :bomId
              and l.lineNo = :lineNo
              and l.lineId <> :lineId
            """)
    boolean existsDuplicateLineNo(@Param("bomId") UUID bomId,
                                  @Param("lineNo") Integer lineNo,
                                  @Param("lineId") UUID lineId);
}
