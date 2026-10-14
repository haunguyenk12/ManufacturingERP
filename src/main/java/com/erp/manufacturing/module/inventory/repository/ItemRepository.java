package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    boolean existsByCompanyCompanyIdAndCode(UUID companyId, String code);

    /**
     * G2-4: the item list with every filter FE sends — free text on code/name, status, type and lot
     * tracking — applied in SQL before pagination, so totals reflect the filter. All four are optional.
     *
     * <p>{@code cast(:search as string)} is load-bearing (CLAUDE.md §0.24): a {@code null} search would
     * otherwise be bound untyped and rejected as {@code lower(bytea)}. Replaces the old
     * {@code findByCompanyCompanyId} + {@code searchByCodeOrName} pair, which the service had to choose
     * between and which could not filter by anything else. {@code ItemRepositoryIT} proves every null
     * branch on Postgres.
     */
    @Query("""
            select i
            from Item i
            where i.company.companyId = :companyId
              and (:status is null or i.status = :status)
              and (:type is null or i.type = :type)
              and (:lotTracked is null or i.lotTracked = :lotTracked)
              and (cast(:search as string) is null
                   or lower(i.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(i.name) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<Item> search(@Param("companyId") UUID companyId,
                      @Param("search") String search,
                      @Param("status") ItemStatus status,
                      @Param("type") ItemType type,
                      @Param("lotTracked") Boolean lotTracked,
                      Pageable pageable);

    /**
     * Batch counterpart of {@link #existsByCompanyCompanyIdAndCode} — one query for a whole set of
     * codes instead of one per code (rule C14).
     *
     * <p>Added for the spreadsheet importer, which sees a few thousand codes at once and must answer
     * "which of these already exist?" without a round trip per row. Codes are compared exactly as
     * stored, so callers normalise (trim + upper) first, the same way {@code ItemService} does.
     */
    List<Item> findByCompanyCompanyIdAndCodeIn(UUID companyId, Collection<String> codes);

    /**
     * Row lock on the item (G2-3). {@code ItemWarehouseSettingService.upsert} takes it before reading
     * the other warehouses' default flags, so two concurrent upserts for the same item serialize and
     * trigger {@code trg_item_warehouse_default_role} (V66) never sees two claimants of one role.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.itemId = :itemId")
    Optional<Item> lockByItemId(@Param("itemId") UUID itemId);
}
