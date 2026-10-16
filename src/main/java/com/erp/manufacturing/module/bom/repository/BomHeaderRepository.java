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

    boolean existsByCompanyCompanyIdAndBomCodeIgnoreCase(UUID companyId, String bomCode);

    @Query("""
            select count(b) > 0
            from BomHeader b
            where b.company.companyId = :companyId
              and upper(b.bomCode) = upper(:bomCode)
              and b.bomId <> :bomId
            """)
    boolean existsBomCodeOnOtherBom(@Param("companyId") UUID companyId,
                                    @Param("bomCode") String bomCode,
                                    @Param("bomId") UUID bomId);

    /** 0 when the product has no BOM yet; the caller holds the item lock, so max + 1 is free (G5). */
    @Query("""
            select coalesce(max(b.versionNumber), 0)
            from BomHeader b
            where b.company.companyId = :companyId
              and b.parentItem.itemId = :parentItemId
            """)
    int findMaxVersionNumber(@Param("companyId") UUID companyId, @Param("parentItemId") UUID parentItemId);

    /** Product of a BOM without loading it, so the family lock can be taken before the BOM is read. */
    @Query("select b.parentItem.itemId from BomHeader b where b.bomId = :bomId")
    Optional<UUID> findParentItemIdByBomId(@Param("bomId") UUID bomId);

    /**
     * {@code search} matches the BOM code, product code or product name. {@code cast(... as string)}
     * is load-bearing: without it a {@code null} search makes Postgres resolve {@code ||} as
     * {@code bytea} and the whole list answers 500 (CLAUDE.md §0.24). {@code latestOnly} keeps only
     * the highest {@code versionNumber} of each product — applied before the status filter's rows are
     * paged, so a page never mixes "latest" with older versions.
     */
    @Query("""
            select b
            from BomHeader b
            where b.company.companyId = :companyId
              and (:parentItemId is null or b.parentItem.itemId = :parentItemId)
              and (:status is null or b.status = :status)
              and (:search is null
                   or lower(b.bomCode) like lower(concat('%', cast(:search as string), '%'))
                   or lower(b.parentItem.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(b.parentItem.name) like lower(concat('%', cast(:search as string), '%')))
              and (:latestOnly = false
                   or b.versionNumber = (select max(b2.versionNumber)
                                         from BomHeader b2
                                         where b2.company = b.company
                                           and b2.parentItem = b.parentItem))
            """)
    Page<BomHeader> search(@Param("companyId") UUID companyId,
                           @Param("parentItemId") UUID parentItemId,
                           @Param("status") BomStatus status,
                           @Param("search") String search,
                           @Param("latestOnly") boolean latestOnly,
                           Pageable pageable);

    /** Every version of one product in one company — the family a BOM belongs to (G5). */
    Page<BomHeader> findByCompanyCompanyIdAndParentItemItemId(UUID companyId, UUID parentItemId, Pageable pageable);

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

    @Query("""
            select distinct b.parentItem.itemId
            from BomHeader b
            where b.company.companyId = :companyId
              and b.parentItem.itemId in :itemIds
            """)
    List<UUID> findParentItemIdsWithBom(@Param("companyId") UUID companyId, @Param("itemIds") Collection<UUID> itemIds);

    @Query("""
            select upper(b.bomCode)
            from BomHeader b
            where b.company.companyId = :companyId
              and upper(b.bomCode) in :codes
            """)
    List<String> findTakenBomCodes(@Param("companyId") UUID companyId, @Param("codes") Collection<String> codes);
}
