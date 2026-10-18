package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.SupplyConversionTargetType;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionConversion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SupplySuggestionConversionRepository extends JpaRepository<SupplySuggestionConversion, UUID> {

    interface SuggestionQuantityProjection {
        UUID getSupplySuggestionId();
        /** Quantity of the rows still held — a released row (G7a) gives its quantity back. */
        BigDecimal getQuantity();
        /** Every row, released or not: "the ledger has touched this proposal". */
        Long getRowCount();
    }

    /** Converted quantity per proposal — one query for a page (C14). */
    @Query("""
            select c.supplySuggestionId as supplySuggestionId,
                   coalesce(sum(case when c.releasedAt is null then c.quantity else 0 end), 0) as quantity,
                   count(c) as rowCount
            from SupplySuggestionConversion c
            where c.supplySuggestionId in :suggestionIds
            group by c.supplySuggestionId
            """)
    List<SuggestionQuantityProjection> sumBySuggestionIds(@Param("suggestionIds") Collection<UUID> suggestionIds);

    /** G7a: rows a purchase order still holds — what cancelling it gives back. */
    List<SupplySuggestionConversion> findByTargetTypeAndTargetIdAndReleasedAtIsNull(
            SupplyConversionTargetType targetType, UUID targetId);

    /** G7a: the planning sources of purchase order lines, released rows included (trace). */
    List<SupplySuggestionConversion> findByTargetTypeAndTargetLineIdInOrderByCreatedAtAscEntryNoAsc(
            SupplyConversionTargetType targetType, Collection<UUID> targetLineIds);

    /** G7a: which of these documents still hold planning quota — one query for a page (C14). */
    @Query("""
            select distinct c.targetId
            from SupplySuggestionConversion c
            where c.targetType = :targetType
              and c.targetId in :targetIds
              and c.releasedAt is null
            """)
    List<UUID> findHoldingTargetIds(@Param("targetType") SupplyConversionTargetType targetType,
                                    @Param("targetIds") Collection<UUID> targetIds);

    /** Replay lookup; scope matches {@code uk_supply_suggestion_conversions_run_key_entry}. */
    List<SupplySuggestionConversion> findByMrpRunIdAndIdempotencyKeyOrderByEntryNoAsc(UUID mrpRunId, String idempotencyKey);

    Page<SupplySuggestionConversion> findByMrpRunId(UUID mrpRunId, Pageable pageable);

    List<SupplySuggestionConversion> findByRequirementLineIdOrderByCreatedAtAscEntryNoAsc(UUID requirementLineId);
}
