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

    @Query("""
            select b.item.itemId as itemId, coalesce(sum(b.quantity - b.reservedQuantity), 0) as quantity
            from StockBalance b
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (b.lot is null or b.lot.status = :availableStatus)
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
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (b.lot is null or b.lot.status = :availableStatus)
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
            where b.item.itemId in :itemIds
              and b.warehouse.warehouseId in :warehouseIds
              and (b.lot is null or b.lot.status = :availableStatus)
            group by b.item.itemId
            """)
    List<StockPlanningQuantityProjection> aggregatePlanningQuantities(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("availableStatus") LotStatus availableStatus);
}
