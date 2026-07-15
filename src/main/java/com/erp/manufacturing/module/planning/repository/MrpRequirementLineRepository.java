package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.MrpRequirementLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MrpRequirementLineRepository extends JpaRepository<MrpRequirementLine, UUID> {

    @EntityGraph(attributePaths = {"mrpRun", "parentRequirementLine", "sourceDemand", "item", "warehouse"})
    Page<MrpRequirementLine> findByMrpRunMrpRunId(UUID mrpRunId, Pageable pageable);

    @EntityGraph(attributePaths = {"mrpRun", "parentRequirementLine", "sourceDemand", "item", "warehouse"})
    List<MrpRequirementLine> findByMrpRunMrpRunIdOrderByRequirementLevelAscDueDateAsc(UUID mrpRunId);
}
