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

    @Query("""
            select r
            from RoutingHeader r
            where r.company.companyId = :companyId
              and (:itemId is null or r.item.itemId = :itemId)
              and (:status is null or r.status = :status)
            """)
    Page<RoutingHeader> search(@Param("companyId") UUID companyId,
                               @Param("itemId") UUID itemId,
                               @Param("status") RoutingStatus status,
                               Pageable pageable);

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
