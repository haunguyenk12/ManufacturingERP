package com.erp.manufacturing.module.planning.repository;

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
        BigDecimal getQuantity();
    }

    /** Converted quantity per proposal — one query for a page (C14). */
    @Query("""
            select c.supplySuggestionId as supplySuggestionId, sum(c.quantity) as quantity
            from SupplySuggestionConversion c
            where c.supplySuggestionId in :suggestionIds
            group by c.supplySuggestionId
            """)
    List<SuggestionQuantityProjection> sumBySuggestionIds(@Param("suggestionIds") Collection<UUID> suggestionIds);

    /** Replay lookup; scope matches {@code uk_supply_suggestion_conversions_run_key_entry}. */
    List<SupplySuggestionConversion> findByMrpRunIdAndIdempotencyKeyOrderByEntryNoAsc(UUID mrpRunId, String idempotencyKey);

    Page<SupplySuggestionConversion> findByMrpRunId(UUID mrpRunId, Pageable pageable);

    List<SupplySuggestionConversion> findByRequirementLineIdOrderByCreatedAtAscEntryNoAsc(UUID requirementLineId);
}
