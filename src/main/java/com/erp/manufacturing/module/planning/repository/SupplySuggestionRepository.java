package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.SupplySuggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplySuggestionRepository extends JpaRepository<SupplySuggestion, UUID> {

    @EntityGraph(attributePaths = {"mrpRun", "requirementLine", "company", "plant", "warehouse", "item"})
    Optional<SupplySuggestion> findWithDetailsBySupplySuggestionId(UUID supplySuggestionId);

    @EntityGraph(attributePaths = {"mrpRun", "requirementLine", "company", "plant", "warehouse", "item"})
    Page<SupplySuggestion> findByMrpRunMrpRunId(UUID mrpRunId, Pageable pageable);
}
