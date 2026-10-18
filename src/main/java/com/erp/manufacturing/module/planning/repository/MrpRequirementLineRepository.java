package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.MrpRequirementLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MrpRequirementLineRepository extends JpaRepository<MrpRequirementLine, UUID> {

    @EntityGraph(attributePaths = {"mrpRun", "parentRequirementLine", "sourceDemand", "item", "warehouse"})
    Page<MrpRequirementLine> findByMrpRunMrpRunId(UUID mrpRunId, Pageable pageable);

    @EntityGraph(attributePaths = {"mrpRun", "parentRequirementLine", "sourceDemand", "item", "warehouse"})
    List<MrpRequirementLine> findByMrpRunMrpRunIdOrderByRequirementLevelAscDueDateAsc(UUID mrpRunId);

    @EntityGraph(attributePaths = {"mrpRun", "parentRequirementLine", "sourceDemand", "item", "warehouse"})
    Optional<MrpRequirementLine> findByMrpRunMrpRunIdAndMrpRequirementLineId(UUID mrpRunId, UUID mrpRequirementLineId);

    /**
     * Items of the run that may have a BOM. Their active BOM and routing are part of the run's
     * source version, so the set has to be the same one the run computed it from (G6b).
     */
    @Query("""
            select distinct l.item.itemId
            from MrpRequirementLine l
            where l.mrpRun.mrpRunId = :mrpRunId
              and l.item.type in :types
            """)
    List<UUID> findDistinctItemIdsByRunAndTypes(@Param("mrpRunId") UUID mrpRunId,
                                                @Param("types") Collection<ItemType> types);
}
