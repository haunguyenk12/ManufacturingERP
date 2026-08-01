package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.SupplySuggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplySuggestionRepository extends JpaRepository<SupplySuggestion, UUID> {

    /**
     * {@code requirementLine.sourceDemand} is fetched because converting a MAKE proposal has to walk
     * it to find the sales order line the work order should be allocated to (F6, spec §2.4).
     */
    @EntityGraph(attributePaths = {
            "mrpRun", "requirementLine", "requirementLine.sourceDemand",
            "company", "plant", "warehouse", "item"})
    Optional<SupplySuggestion> findWithDetailsBySupplySuggestionId(UUID supplySuggestionId);

    @EntityGraph(attributePaths = {"mrpRun", "requirementLine", "company", "plant", "warehouse", "item"})
    Page<SupplySuggestion> findByMrpRunMrpRunId(UUID mrpRunId, Pageable pageable);
}
