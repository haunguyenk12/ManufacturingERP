package com.erp.manufacturing.module.costing.repository;

import com.erp.manufacturing.module.costing.domain.ItemStandardCost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ItemStandardCostRepository extends JpaRepository<ItemStandardCost, UUID> {

    Optional<ItemStandardCost> findByItemItemId(UUID itemId);

    /**
     * {@code itemId} is compared with plain {@code =} against a UUID column, not a nullable
     * {@code String} flowing into {@code concat}/{@code like} — the "lower(bytea)" hazard
     * (CLAUDE.md §0.24) does not apply here, same reasoning as {@code WorkCenterRepository.search}.
     */
    @Query("""
            select c
            from ItemStandardCost c
            where c.company.companyId = :companyId
              and (:itemId is null or c.item.itemId = :itemId)
            """)
    Page<ItemStandardCost> search(@Param("companyId") UUID companyId,
                                  @Param("itemId") UUID itemId,
                                  Pageable pageable);
}
