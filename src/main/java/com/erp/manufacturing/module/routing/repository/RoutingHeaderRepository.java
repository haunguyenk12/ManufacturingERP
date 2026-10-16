package com.erp.manufacturing.module.routing.repository;

import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
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

public interface RoutingHeaderRepository extends JpaRepository<RoutingHeader, UUID> {

    boolean existsByCompanyCompanyIdAndCodeAndRoutingVersion(UUID companyId, String code, String routingVersion);

    /**
     * {@code plantId} keeps routings with at least one operation on a work center of that plant —
     * routing stays company-scoped (G5, D5) and all operations of one routing share a plant
     * ({@code B_wc2}), so this is the routing's plant. {@code search} matches the routing code,
     * product code or product name; the {@code cast} keeps a null search from turning {@code ||} into
     * {@code bytea} (CLAUDE.md §0.24).
     */
    @Query("""
            select r
            from RoutingHeader r
            where r.company.companyId = :companyId
              and (:itemId is null or r.item.itemId = :itemId)
              and (:status is null or r.status = :status)
              and (:plantId is null or exists (select 1 from RoutingOperation op
                                               where op.routing = r
                                                 and op.workCenter.plant.plantId = :plantId))
              and (:workCenterId is null or exists (select 1 from RoutingOperation wo
                                                    where wo.routing = r
                                                      and wo.workCenter.workCenterId = :workCenterId))
              and (:search is null
                   or lower(r.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(r.item.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(r.item.name) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<RoutingHeader> search(@Param("companyId") UUID companyId,
                               @Param("itemId") UUID itemId,
                               @Param("status") RoutingStatus status,
                               @Param("plantId") UUID plantId,
                               @Param("workCenterId") UUID workCenterId,
                               @Param("search") String search,
                               Pageable pageable);

    /** 0 when the product has no routing yet; the caller holds the item lock (G5). */
    @Query("""
            select coalesce(max(r.versionNumber), 0)
            from RoutingHeader r
            where r.company.companyId = :companyId
              and r.item.itemId = :itemId
            """)
    int findMaxVersionNumber(@Param("companyId") UUID companyId, @Param("itemId") UUID itemId);

    @Query("select r.item.itemId from RoutingHeader r where r.routingId = :routingId")
    Optional<UUID> findItemIdByRoutingId(@Param("routingId") UUID routingId);

    boolean existsByCompanyCompanyIdAndItemItemIdAndStatus(UUID companyId, UUID itemId, RoutingStatus status);

    /** A routing code names one product's family (FE §3.5): the same code on another product is refused. */
    @Query("""
            select count(r) > 0
            from RoutingHeader r
            where r.company.companyId = :companyId
              and r.code = :code
              and r.item.itemId <> :itemId
            """)
    boolean existsCodeOnOtherItem(@Param("companyId") UUID companyId,
                                  @Param("code") String code,
                                  @Param("itemId") UUID itemId);

    /** Every version of one product in one company — the routing family (G5). */
    Page<RoutingHeader> findByCompanyCompanyIdAndItemItemId(UUID companyId, UUID itemId, Pageable pageable);

    /** G5 (FE Q9): an ACTIVE routing still sends work to this work center. */
    @Query("""
            select count(r) > 0
            from RoutingHeader r join r.operations op
            where r.status = com.erp.manufacturing.module.routing.domain.RoutingStatus.ACTIVE
              and op.workCenter.workCenterId = :workCenterId
            """)
    boolean existsActiveRoutingUsingWorkCenter(@Param("workCenterId") UUID workCenterId);

    @EntityGraph(attributePaths = {"operations", "item", "company"})
    Optional<RoutingHeader> findWithOperationsByRoutingId(UUID routingId);

    @EntityGraph(attributePaths = {"operations", "item", "company"})
    Optional<RoutingHeader> findWithOperationsByCompanyCompanyIdAndItemItemIdAndStatus(
            UUID companyId, UUID itemId, RoutingStatus status);

    Optional<RoutingHeader> findByCompanyCompanyIdAndItemItemIdAndStatus(
            UUID companyId, UUID itemId, RoutingStatus status);

    /**
     * Batch sibling of {@link #findByCompanyCompanyIdAndItemItemIdAndStatus} — MRP asks about a whole
     * BOM level at once, so it must not loop per item (rule C14). Operations are deliberately not
     * fetched: planning only needs to know whether an ACTIVE routing exists.
     */
    List<RoutingHeader> findByCompanyCompanyIdAndItemItemIdInAndStatus(
            UUID companyId, Collection<UUID> itemIds, RoutingStatus status);
}
