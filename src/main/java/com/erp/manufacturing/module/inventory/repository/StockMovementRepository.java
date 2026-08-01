package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.MovementType;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /**
     * Replay lookup. Scoped by {@link MovementType} because one {@code Idempotency-Key} used to match
     * across operations — a key sent to {@code /receive} then {@code /issue} returned the RECEIVE
     * document (debt #10, fixed in {@code V37}). The scope here must stay in step with
     * {@code uk_stock_movements_idempotency_key}: a narrower query than the constraint returns a
     * non-deterministic row, a wider one lets the DB reject a legitimate document.
     */
    Optional<StockMovement> findByIdempotencyKeyAndMovementType(String idempotencyKey, MovementType movementType);

    Page<StockMovement> findByWarehouseWarehouseId(UUID warehouseId, Pageable pageable);

    Page<StockMovement> findByWarehouseWarehouseIdAndItemItemId(UUID warehouseId, UUID itemId, Pageable pageable);

    Page<StockMovement> findByWarehouseWarehouseIdAndLotLotId(UUID warehouseId, UUID lotId, Pageable pageable);

    Page<StockMovement> findByWarehouseWarehouseIdAndItemItemIdAndLotLotId(
            UUID warehouseId, UUID itemId, UUID lotId, Pageable pageable);

    @Query("""
            select m
            from StockMovement m
            where m.warehouse.warehouseId in :warehouseIds
            order by m.createdAt desc
            """)
    List<StockMovement> findRecentByWarehouseIds(@Param("warehouseIds") Collection<UUID> warehouseIds, Pageable pageable);
}
