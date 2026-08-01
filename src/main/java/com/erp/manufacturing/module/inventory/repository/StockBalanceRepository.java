package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceRepository extends JpaRepository<StockBalance, UUID> {

    Optional<StockBalance> findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(UUID itemId, UUID warehouseId);

    Optional<StockBalance> findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(UUID itemId, UUID warehouseId, UUID lotId);

    Page<StockBalance> findByWarehouseWarehouseId(UUID warehouseId, Pageable pageable);

    Page<StockBalance> findByWarehouseWarehouseIdAndItemItemId(UUID warehouseId, UUID itemId, Pageable pageable);

    /**
     * Issuable balances of one item across a set of warehouses, in FEFO order — earliest expiry
     * first, then earliest received, with undated (non-lot-tracked) stock last so perishable stock
     * is always consumed before it can expire. Used by automatic work order reservation (F5).
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
              and (l is null or l.status = :availableStatus)
              and b.quantity - b.reservedQuantity > 0
            order by l.expiresAt asc nulls last, l.receivedAt asc nulls last, b.createdAt asc
            """)
    List<StockBalance> findIssuableBalancesFefo(@Param("itemId") UUID itemId,
                                                @Param("warehouseIds") Collection<UUID> warehouseIds,
                                                @Param("availableStatus") LotStatus availableStatus);

    /**
     * The {@code left join b.lot l} is load-bearing: dereferencing {@code b.lot.status} as a path
     * expression makes Hibernate emit an INNER JOIN, which drops every non-lot-tracked balance
     * ({@code lot_id IS NULL}) before the {@code l is null} branch can match it — violating B3.
     */
    @Query("""
            select b.item.itemId as itemId, coalesce(sum(b.quantity - b.reservedQuantity), 0) as quantity
            from StockBalance b
            left join b.lot l
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (l is null or l.status = :availableStatus)
            group by b.item.itemId
            """)
    List<StockAvailabilityProjection> aggregateAvailableQuantities(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("availableStatus") LotStatus availableStatus);

    @Query("""
            select b.item.itemId as itemId,
                   b.warehouse.warehouseId as warehouseId,
                   coalesce(sum(b.quantity - b.reservedQuantity), 0) as quantity
            from StockBalance b
            left join b.lot l
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (l is null or l.status = :availableStatus)
            group by b.item.itemId, b.warehouse.warehouseId
            """)
    List<StockAvailabilityByWarehouseProjection> aggregateAvailableQuantitiesByWarehouse(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("availableStatus") LotStatus availableStatus);

    @Query("""
            select b.item.itemId as itemId,
                   coalesce(sum(b.quantity), 0) as onHandQuantity,
                   coalesce(sum(b.reservedQuantity), 0) as reservedQuantity,
                   coalesce(sum(b.quantity - b.reservedQuantity), 0) as availableQuantity
            from StockBalance b
            left join b.lot l
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (l is null or l.status = :availableStatus)
            group by b.item.itemId
            """)
    List<StockPlanningQuantityProjection> aggregatePlanningQuantities(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("availableStatus") LotStatus availableStatus);

    /**
     * How many distinct lots holding stock were left out of {@link #aggregatePlanningQuantities}
     * because their status is not {@code AVAILABLE} (B3). MRP surfaces this as
     * {@code excludedLotCount} so a planner can tell "no stock" apart from "stock exists but is on
     * QC hold" (spec §2.4). The inner join is deliberate — a non-lot-tracked balance can never be
     * excluded, so it must not be counted.
     */
    @Query("""
            select b.item.itemId as itemId, count(distinct l.lotId) as excludedLotCount
            from StockBalance b
            join b.lot l
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and l.status <> :availableStatus
              and b.quantity > 0
            group by b.item.itemId
            """)
    List<StockExcludedLotCountProjection> aggregateExcludedLotCounts(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("availableStatus") LotStatus availableStatus);
}
