package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.MaterialReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MaterialReturnLineRepository extends JpaRepository<MaterialReturnLine, UUID> {

    /** Material already returned, per source issue line — one query for the whole batch (C14). */
    @Query("""
            select l.sourceIssueLine.issueLineId as refId, coalesce(sum(l.quantity), 0) as quantity
            from MaterialReturnLine l
            where l.sourceIssueLine.issueLineId in :issueLineIds
            group by l.sourceIssueLine.issueLineId
            """)
    List<KeyedQuantityProjection> sumBySourceIssueLineIds(@Param("issueLineIds") Collection<UUID> issueLineIds);

    /**
     * The lines of a page of returns with everything their response names — source issue, item, warehouse, lot,
     * movement — in one query (C14). Every path is a to-one association, so a single join-fetch is safe.
     */
    @Query("""
            select l from MaterialReturnLine l
            join fetch l.materialReturn r
            join fetch l.sourceIssueLine s
            join fetch s.issue
            join fetch s.componentLine
            join fetch s.item
            join fetch s.warehouse
            left join fetch s.lot
            join fetch l.stockMovement
            where r.materialReturnId in :returnIds
            order by l.lineNo
            """)
    List<MaterialReturnLine> findWithSourceByMaterialReturnIds(@Param("returnIds") Collection<UUID> returnIds);

    /** G11b: every return line of these issue lines, in posting order — the order return values are decided in. */
    @Query("""
            select l from MaterialReturnLine l
            join fetch l.materialReturn r
            where l.sourceIssueLine.issueLineId in :issueLineIds
              and r.status = com.erp.manufacturing.module.workorder.domain.MaterialReturnStatus.POSTED
            order by r.postedAt, r.materialReturnId, l.lineNo
            """)
    List<MaterialReturnLine> findPostedBySourceIssueLineIds(@Param("issueLineIds") Collection<UUID> issueLineIds);
}
