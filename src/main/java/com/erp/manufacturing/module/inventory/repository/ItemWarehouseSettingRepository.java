package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSetting;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSettingStatus;
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

public interface ItemWarehouseSettingRepository extends JpaRepository<ItemWarehouseSetting, UUID> {

    @EntityGraph(attributePaths = {"item", "warehouse", "warehouse.plant", "warehouse.plant.company"})
    Optional<ItemWarehouseSetting> findByItemItemIdAndWarehouseWarehouseIdAndStatus(
            UUID itemId, UUID warehouseId, ItemWarehouseSettingStatus status);

    @EntityGraph(attributePaths = {"item", "warehouse", "warehouse.plant", "warehouse.plant.company"})
    Optional<ItemWarehouseSetting> findWithDetailsBySettingId(UUID settingId);

    @EntityGraph(attributePaths = {"item", "warehouse", "warehouse.plant", "warehouse.plant.company"})
    @Query("""
            select s
            from ItemWarehouseSetting s
            where (:warehouseId is null or s.warehouse.warehouseId = :warehouseId)
              and (:itemId is null or s.item.itemId = :itemId)
              and (:status is null or s.status = :status)
            """)
    Page<ItemWarehouseSetting> search(@Param("warehouseId") UUID warehouseId,
                                      @Param("itemId") UUID itemId,
                                      @Param("status") ItemWarehouseSettingStatus status,
                                      Pageable pageable);

    @EntityGraph(attributePaths = {"item", "warehouse", "warehouse.plant", "warehouse.plant.company"})
    List<ItemWarehouseSetting> findByWarehouseWarehouseIdInAndStatus(
            Collection<UUID> warehouseIds, ItemWarehouseSettingStatus status);

    @Query("""
            select s.item.itemId as itemId,
                   coalesce(sum(s.safetyStock), 0) as safetyStockQuantity,
                   coalesce(sum(s.reorderPoint), 0) as reorderPointQuantity,
                   coalesce(max(s.leadTimeDays), 0) as leadTimeDays
            from ItemWarehouseSetting s
            where s.item.itemId in :itemIds
              and s.warehouse.warehouseId in :warehouseIds
              and s.status = :status
            group by s.item.itemId
            """)
    List<ItemWarehousePlanningSettingProjection> aggregatePlanningSettings(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("status") ItemWarehouseSettingStatus status);
}
