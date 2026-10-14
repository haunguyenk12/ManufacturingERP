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

    /**
     * Most recently touched INACTIVE row of a pair. G2-3: an upsert of a pair that was deactivated
     * reactivates this row (keeping its {@code settingId}) instead of inserting a second row for the
     * same item and warehouse.
     */
    @EntityGraph(attributePaths = {"item", "warehouse", "warehouse.plant", "warehouse.plant.company"})
    Optional<ItemWarehouseSetting> findFirstByItemItemIdAndWarehouseWarehouseIdAndStatusOrderByUpdatedAtDesc(
            UUID itemId, UUID warehouseId, ItemWarehouseSettingStatus status);

    /**
     * {@code plantId} (G2-3) closes a scope leak: without it, a call that named no warehouse returned the
     * settings of every company the caller could see. All three filters are optional and combine.
     * {@code ItemWarehouseSettingRepositoryIT} proves each null branch on Postgres.
     */
    @EntityGraph(attributePaths = {"item", "warehouse", "warehouse.plant", "warehouse.plant.company"})
    @Query("""
            select s
            from ItemWarehouseSetting s
            where (:plantId is null or s.warehouse.plant.plantId = :plantId)
              and (:warehouseId is null or s.warehouse.warehouseId = :warehouseId)
              and (:itemId is null or s.item.itemId = :itemId)
              and (:status is null or s.status = :status)
            """)
    Page<ItemWarehouseSetting> search(@Param("plantId") UUID plantId,
                                      @Param("warehouseId") UUID warehouseId,
                                      @Param("itemId") UUID itemId,
                                      @Param("status") ItemWarehouseSettingStatus status,
                                      Pageable pageable);

    @EntityGraph(attributePaths = {"item", "warehouse", "warehouse.plant", "warehouse.plant.company"})
    List<ItemWarehouseSetting> findByWarehouseWarehouseIdInAndStatus(
            Collection<UUID> warehouseIds, ItemWarehouseSettingStatus status);

    @EntityGraph(attributePaths = {"item", "warehouse", "warehouse.plant"})
    List<ItemWarehouseSetting> findByItemItemIdAndWarehousePlantPlantIdAndStatus(
            UUID itemId, UUID plantId, ItemWarehouseSettingStatus status);

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
