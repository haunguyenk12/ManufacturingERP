package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceRepository extends JpaRepository<StockBalance, UUID> {

    Optional<StockBalance> findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(UUID itemId, UUID warehouseId);

    Optional<StockBalance> findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(UUID itemId, UUID warehouseId, UUID lotId);

    Page<StockBalance> findByWarehouseWarehouseId(UUID warehouseId, Pageable pageable);

    Page<StockBalance> findByWarehouseWarehouseIdAndItemItemId(UUID warehouseId, UUID itemId, Pageable pageable);

    @Query(value = """
            select b.item.itemId as itemId,
                   b.item.code as itemCode,
                   b.item.name as itemName,
                   b.item.unit as uom,
                   b.warehouse.warehouseId as warehouseId,
                   coalesce(sum(b.quantity), 0) as onHandQuantity,
                   coalesce(sum(b.reservedQuantity), 0) as reservedQuantity,
                   coalesce(sum(case when l is null
                                          or (l.status = com.erp.manufacturing.module.inventory.domain.LotStatus.AVAILABLE
                                              and (l.expiresAt is null or l.expiresAt > :now))
                                     then b.quantity - b.reservedQuantity - b.qualityHoldQuantity else 0 end), 0)
                       as availableQuantity,
                   coalesce(sum(b.qualityHoldQuantity), 0) as qualityHoldQuantity,
                   coalesce(sum(case when l.status = com.erp.manufacturing.module.inventory.domain.LotStatus.HOLD
                                     then b.quantity else 0 end), 0) as heldLotQuantity,
                   coalesce(sum(case when l.status = com.erp.manufacturing.module.inventory.domain.LotStatus.REJECTED
                                     then b.quantity else 0 end), 0) as rejectedQuantity,
                   coalesce(sum(case when l.status = com.erp.manufacturing.module.inventory.domain.LotStatus.EXPIRED
                                          or (l.status = com.erp.manufacturing.module.inventory.domain.LotStatus.AVAILABLE and l.expiresAt <= :now)
                                     then b.quantity else 0 end), 0) as expiredQuantity,
                   count(distinct l.lotId) as lotCount,
                   max(b.updatedAt) as updatedAt
            from StockBalance b
            left join b.lot l
            where b.warehouse.warehouseId = :warehouseId
              and (:itemId is null or b.item.itemId = :itemId)
              and (cast(:search as string) is null
                   or lower(b.item.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(b.item.name) like lower(concat('%', cast(:search as string), '%')))
            group by b.item.itemId, b.item.code, b.item.name, b.item.unit, b.warehouse.warehouseId
            """, countQuery = """
            select count(distinct b.item.itemId)
            from StockBalance b
            where b.warehouse.warehouseId = :warehouseId
              and (:itemId is null or b.item.itemId = :itemId)
              and (cast(:search as string) is null
                   or lower(b.item.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(b.item.name) like lower(concat('%', cast(:search as string), '%')))
            """)
    /*
     * G4 (user decision 2026-10-15): heldLotQuantity = stock in HOLD lots, kept apart from
     * qualityHoldQuantity, which stays the carrier for stock with no lot (B2/B116). An AVAILABLE lot
     * whose expiresAt is not after :now is reported as expired, not available — the same rule the
     * manual issue/transfer/count gates apply. Buckets available/heldLot/rejected/expired never overlap.
     */
    Page<StockBalanceAggregateProjection> aggregateBalances(
            @Param("warehouseId") UUID warehouseId,
            @Param("itemId") UUID itemId,
            @Param("search") String search,
            @Param("now") Instant now,
            Pageable pageable);

    /**
     * All warehouse rows for one lot (C2-2). {@code uk_stock_balances_item_warehouse_lot} is unique
     * per {@code (item, warehouse, lot)}, not per {@code (item, lot)} — a lot can legitimately have
     * stock in more than one warehouse, so this returns a list, not an {@code Optional}.
     */
    List<StockBalance> findByLotLotId(UUID lotId);

    /**
     * Every warehouse where the lot still has stock (G4-1). Lot status is global — one row on
     * {@code inventory_lots} — so a status change acts on all of these at once, and the caller must be
     * allowed in each of them, not only in the warehouse named in the request.
     */
    @Query("""
            select distinct b.warehouse.warehouseId
            from StockBalance b
            where b.lot.lotId = :lotId
              and b.quantity > 0
            """)
    List<UUID> findWarehouseIdsHoldingLot(@Param("lotId") UUID lotId);

    /** True while any position of the lot carries reserved quantity, i.e. an ACTIVE reservation (G4-1). */
    boolean existsByLotLotIdAndReservedQuantityGreaterThan(UUID lotId, BigDecimal reservedQuantity);

    /**
     * Locks one stock position (non-lot) for a read-modify-write that must see the latest committed
     * quantity — the stock count (G4-3) compares {@code version} and derives its delta from it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from StockBalance b
            where b.item.itemId = :itemId
              and b.warehouse.warehouseId = :warehouseId
              and b.lot is null
            """)
    Optional<StockBalance> lockPositionWithoutLot(@Param("itemId") UUID itemId,
                                                  @Param("warehouseId") UUID warehouseId);

    /** Lot-tracked variant of {@link #lockPositionWithoutLot}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from StockBalance b
            where b.item.itemId = :itemId
              and b.warehouse.warehouseId = :warehouseId
              and b.lot.lotId = :lotId
            """)
    Optional<StockBalance> lockPositionWithLot(@Param("itemId") UUID itemId,
                                               @Param("warehouseId") UUID warehouseId,
                                               @Param("lotId") UUID lotId);

    /**
     * Locks every position of the given items in one warehouse, <b>ordered by {@code balanceId}</b>
     * (G4-2, B151). Two transfers touching overlapping positions therefore take their locks in the same
     * order and cannot deadlock. Locking a superset (other lots of the same items) is cheap and keeps
     * this a single statement. No fetch join: Postgres refuses {@code FOR UPDATE} on the nullable side
     * of an outer join, and the lots are already in the persistence context by the time this runs.
     * <p>
     * 🔴 The lock is load-bearing: without it two transfers both read the same free quantity, and the
     * loser fails on {@code @Version} with a generic 409 instead of the per-line
     * {@code INSUFFICIENT_AVAILABLE_STOCK} (mutation-tested, {@code StockTransferIT}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from StockBalance b
            where b.warehouse.warehouseId = :warehouseId
              and b.item.itemId in :itemIds
            order by b.balanceId
            """)
    List<StockBalance> lockPositionsOfItems(@Param("warehouseId") UUID warehouseId,
                                            @Param("itemIds") Collection<UUID> itemIds);

    /**
     * Lot list/search (C2-2). {@code warehouseId} is required — same convention as
     * {@link #findByWarehouseWarehouseId} and every other endpoint in this module that reads
     * {@code stock_balances}. {@code l is not null} excludes non-lot-tracked balances, which have
     * nothing to list here. {@code join fetch} on {@code lot}/{@code item}/{@code warehouse} is safe
     * with pagination because all three are {@code @ManyToOne} (to-one), not collections (rule C13).
     * <p>
     * {@code cast(:search as string)} / {@code cast(:expiryFrom as timestamp)} /
     * {@code cast(:expiryTo as timestamp)} are mandatory, not defensive — a bare nullable
     * {@code String} inside {@code lower(... || ... || ...)} makes Postgres pick the {@code bytea}
     * overload of {@code ||} and fail at parse time ({@code lower(bytea)}, {@code CLAUDE.md §0.24}),
     * and a bare {@code Instant} that only appears in an {@code IS NULL} check has no other type
     * context to infer from ({@code CLAUDE.md §0.36}). Both fail the *entire* query, not just the
     * filter, whenever the parameter is {@code null} — which is the common case here.
     */
    @Query("""
            select b
            from StockBalance b
            join fetch b.lot l
            join fetch b.item i
            join fetch b.warehouse w
            where b.warehouse.warehouseId = :warehouseId
              and l is not null
              and (:itemId is null or i.itemId = :itemId)
              and (:status is null or l.status = :status)
              and (cast(:search as string) is null
                   or lower(l.lotCode) like lower(concat('%', cast(:search as string), '%')))
              and (cast(:expiryFrom as timestamp) is null or l.expiresAt >= :expiryFrom)
              and (cast(:expiryTo as timestamp) is null or l.expiresAt <= :expiryTo)
            """)
    Page<StockBalance> searchLots(@Param("warehouseId") UUID warehouseId,
                                  @Param("itemId") UUID itemId,
                                  @Param("status") LotStatus status,
                                  @Param("search") String search,
                                  @Param("expiryFrom") Instant expiryFrom,
                                  @Param("expiryTo") Instant expiryTo,
                                  Pageable pageable);

    /**
     * Issuable balances of one item across a set of warehouses, in FEFO order — earliest expiry
     * first, then earliest received, with undated (non-lot-tracked) stock last so perishable stock
     * is always consumed before it can expire. Used by automatic work order reservation (F5).
     * CF-2 (B264): a lot whose {@code expiresAt} is not after {@code now} is not issuable and is skipped.
     * <p>
     * Same {@code left join b.lot l} requirement as {@link #aggregateAvailableQuantities} — see the
     * note there.
     */
    @Query("""
            select b
            from StockBalance b
            left join b.lot l
            where b.item.itemId = :itemId
              and b.warehouse.warehouseId in :warehouseIds
              and (l is null or (l.status = :availableStatus and (l.expiresAt is null or l.expiresAt > :now)))
              and b.quantity - b.reservedQuantity - b.qualityHoldQuantity > 0
            order by l.expiresAt asc nulls last, l.receivedAt asc nulls last, b.createdAt asc
            """)
    List<StockBalance> findIssuableBalancesFefo(@Param("itemId") UUID itemId,
                                                @Param("warehouseIds") Collection<UUID> warehouseIds,
                                                @Param("availableStatus") LotStatus availableStatus,
                                                @Param("now") Instant now);

    /**
     * Positions stock can be held from for a sales order line (G6a): free quantity above zero, lot
     * {@code AVAILABLE} <b>and not expired</b> — the same eligibility as {@link #findIssuableBalancesFefo}
     * since CF-2, paged. Same FEFO order, with a stable id tie-break so pages do not shuffle. Same load-bearing {@code left join} on the lot as every query here.
     */
    @Query(value = """
            select b
            from StockBalance b
            join fetch b.warehouse w
            left join fetch b.lot l
            where b.item.itemId = :itemId
              and w.warehouseId in :warehouseIds
              and (l is null or (l.status = :availableStatus and (l.expiresAt is null or l.expiresAt > :now)))
              and b.quantity - b.reservedQuantity - b.qualityHoldQuantity > 0
            order by l.expiresAt asc nulls last, l.receivedAt asc nulls last, b.createdAt asc, b.balanceId asc
            """, countQuery = """
            select count(b)
            from StockBalance b
            left join b.lot l
            where b.item.itemId = :itemId
              and b.warehouse.warehouseId in :warehouseIds
              and (l is null or (l.status = :availableStatus and (l.expiresAt is null or l.expiresAt > :now)))
              and b.quantity - b.reservedQuantity - b.qualityHoldQuantity > 0
            """)
    Page<StockBalance> findReservableBalancesFefo(@Param("itemId") UUID itemId,
                                                  @Param("warehouseIds") Collection<UUID> warehouseIds,
                                                  @Param("availableStatus") LotStatus availableStatus,
                                                  @Param("now") Instant now,
                                                  Pageable pageable);

    /**
     * The {@code left join b.lot l} is load-bearing: dereferencing {@code b.lot.status} as a path
     * expression makes Hibernate emit an INNER JOIN, which drops every non-lot-tracked balance
     * ({@code lot_id IS NULL}) before the {@code l is null} branch can match it — violating B3.
     * CF-2 (B264): a lot past its {@code expiresAt} (on the server clock) is not available either.
     */
    @Query("""
            select b.item.itemId as itemId,
                   coalesce(sum(b.quantity - b.reservedQuantity - b.qualityHoldQuantity), 0) as quantity
            from StockBalance b
            left join b.lot l
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (l is null or (l.status = :availableStatus and (l.expiresAt is null or l.expiresAt > :now)))
            group by b.item.itemId
            """)
    List<StockAvailabilityProjection> aggregateAvailableQuantities(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("availableStatus") LotStatus availableStatus,
            @Param("now") Instant now);

    /**
     * Same eligibility rule as {@link #aggregateAvailableQuantities} (including the load-bearing
     * {@code left join b.lot l}), but grouped per warehouse and returning the three terms the
     * subtraction is made of, not only its result. The inventory dashboard renders an alert line per
     * {@code (item, warehouse)} and has to be able to explain {@code availableQuantity}.
     * <p>
     * {@code availableQuantity} can never come out negative: {@code chk_stock_balances_reserved_quantity}
     * (V16) and {@code chk_stock_balances_quality_hold_quantity} (V56) together enforce
     * {@code reserved + qualityHold <= quantity} on every row, so no {@code max(0, …)} clamp is needed
     * here — and none is applied, so a violation would surface instead of being hidden.
     */
    @Query("""
            select b.item.itemId as itemId,
                   b.warehouse.warehouseId as warehouseId,
                   coalesce(sum(b.quantity), 0) as onHandQuantity,
                   coalesce(sum(b.reservedQuantity), 0) as reservedQuantity,
                   coalesce(sum(b.qualityHoldQuantity), 0) as qualityHoldQuantity,
                   coalesce(sum(b.quantity - b.reservedQuantity - b.qualityHoldQuantity), 0) as availableQuantity
            from StockBalance b
            left join b.lot l
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (l is null or (l.status = :availableStatus and (l.expiresAt is null or l.expiresAt > :now)))
            group by b.item.itemId, b.warehouse.warehouseId
            """)
    List<StockQuantityByWarehouseProjection> aggregateStockQuantitiesByWarehouse(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("availableStatus") LotStatus availableStatus,
            @Param("now") Instant now);

    @Query("""
            select b.item.itemId as itemId,
                   coalesce(sum(b.quantity), 0) as onHandQuantity,
                   coalesce(sum(b.reservedQuantity), 0) as reservedQuantity,
                   coalesce(sum(b.quantity - b.reservedQuantity - b.qualityHoldQuantity), 0) as availableQuantity
            from StockBalance b
            left join b.lot l
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (l is null or (l.status = :availableStatus and (l.expiresAt is null or l.expiresAt > :now)))
            group by b.item.itemId
            """)
    List<StockPlanningQuantityProjection> aggregatePlanningQuantities(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("availableStatus") LotStatus availableStatus,
            @Param("now") Instant now);

    /**
     * How many distinct lots holding stock were left out of {@link #aggregatePlanningQuantities}
     * because their status is not {@code AVAILABLE} (B3). MRP surfaces this as
     * {@code excludedLotCount} so a planner can tell "no stock" apart from "stock exists but is on
     * QC hold" (spec §2.4). CF-2: an AVAILABLE lot past its expiry is excluded from planning too, so it
     * is counted here. The inner join is deliberate — a non-lot-tracked balance can never be
     * excluded, so it must not be counted.
     */
    @Query("""
            select b.item.itemId as itemId, count(distinct l.lotId) as excludedLotCount
            from StockBalance b
            join b.lot l
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (l.status <> :availableStatus or (l.expiresAt is not null and l.expiresAt <= :now))
              and b.quantity > 0
            group by b.item.itemId
            """)
    List<StockExcludedLotCountProjection> aggregateExcludedLotCounts(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("availableStatus") LotStatus availableStatus,
            @Param("now") Instant now);

    /** G11: everything on hand of one item in one warehouse, every lot and quality bucket included. */
    @Query("""
            select coalesce(sum(b.quantity), 0)
            from StockBalance b
            where b.item.itemId = :itemId and b.warehouse.warehouseId = :warehouseId
            """)
    java.math.BigDecimal sumOnHand(@Param("itemId") UUID itemId, @Param("warehouseId") UUID warehouseId);
}
