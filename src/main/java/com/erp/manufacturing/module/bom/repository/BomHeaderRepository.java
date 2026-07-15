package com.erp.manufacturing.module.bom.repository;

import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BomHeaderRepository extends JpaRepository<BomHeader, UUID> {

    boolean existsByCompanyCompanyIdAndParentItemItemIdAndRevision(UUID companyId, UUID parentItemId, String revision);

    @Query("""
            select b
            from BomHeader b
            where b.company.companyId = :companyId
              and (:parentItemId is null or b.parentItem.itemId = :parentItemId)
              and (:status is null or b.status = :status)
            """)
    Page<BomHeader> search(@Param("companyId") UUID companyId,
                           @Param("parentItemId") UUID parentItemId,
                           @Param("status") BomStatus status,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"lines", "lines.componentItem", "parentItem", "company"})
    Optional<BomHeader> findWithLinesByBomId(UUID bomId);

    @EntityGraph(attributePaths = {"lines", "lines.componentItem", "parentItem", "company"})
    Optional<BomHeader> findWithLinesByCompanyCompanyIdAndParentItemItemIdAndStatus(
            UUID companyId, UUID parentItemId, BomStatus status);

    @EntityGraph(attributePaths = {"lines", "lines.componentItem", "parentItem", "company"})
    List<BomHeader> findWithLinesByCompanyCompanyIdAndParentItemItemIdInAndStatus(
            UUID companyId, Collection<UUID> parentItemIds, BomStatus status);

    Optional<BomHeader> findByCompanyCompanyIdAndParentItemItemIdAndStatus(
            UUID companyId, UUID parentItemId, BomStatus status);
}
