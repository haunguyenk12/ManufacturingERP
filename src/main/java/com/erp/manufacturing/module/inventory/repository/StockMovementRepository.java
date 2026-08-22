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

    /**
     * The earliest {@code RECEIVE} movement for a lot is treated as its origin (C2-2) — used to
     * surface {@code sourceMovementType}/{@code sourceReferenceType}/{@code sourceReferenceId} on the
     * lot detail/list response. Display-only: this does <b>not</b> back the HOLD-escape gate on
     * {@code POST /inventory/lots/{lotId}/status}, which uses
     * {@code LotQcOriginLookupService} instead (module/workorder/CLAUDE.md) because a
     * {@code referenceType} heuristic alone cannot tell a lot that was already properly QC'd once
     * apart from one that never went through QC.
     */
    Optional<StockMovement> findFirstByLotLotIdAndMovementTypeOrderByCreatedAtAsc(
            UUID lotId, MovementType movementType);

    /**
     * Warehouse-scoped variant used by lot detail so a caller cannot infer the source reference of
     * the same lot in a warehouse outside its access scope.
     */
    Optional<StockMovement> findFirstByWarehouseWarehouseIdAndLotLotIdAndMovementTypeOrderByCreatedAtAsc(
            UUID warehouseId, UUID lotId, MovementType movementType);

    /**
     * Newest ledger rows across a scope, for the inventory dashboard.
     * <p>
     * The fetch joins are load-bearing, not an optimisation: the dashboard renders item and warehouse
     * labels for every row, and both associations are {@code LAZY}, so without them a page of N rows
     * costs 2N extra selects. {@code lot} must stay a <b>left</b> join — an inner one would silently
     * drop every movement of a non-lot-tracked item. All three are to-one, so fetching them alongside
     * a {@code Pageable} is safe (rule C13 only bans collections).
     * <p>
     * {@code movementId desc} is the tiebreaker: {@code createdAt} alone leaves rows written in the
     * same transaction (a receipt posting several lines) in an order Postgres is free to change
     * between calls.
     */
    @Query("""
            select m
            from StockMovement m
            join fetch m.item
            join fetch m.warehouse
            left join fetch m.lot
            where m.warehouse.warehouseId in :warehouseIds
            order by m.createdAt desc, m.movementId desc
            """)
    List<StockMovement> findRecentByWarehouseIds(@Param("warehouseIds") Collection<UUID> warehouseIds, Pageable pageable);
}
