package com.erp.manufacturing.module.inventory.mapper;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.*;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class InventoryMapper {

    public ItemResponse toResponse(Item item) {
        return new ItemResponse(
                item.getItemId(),
                item.getCompany().getCompanyId(),
                item.getCode(),
                item.getName(),
                item.getType().name(),
                item.getUnit(),
                item.isLotTracked(),
                item.isSerialTracked(),
                item.getStatus().name(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    public StockBalanceResponse toResponse(StockBalance balance) {
        InventoryLot lot = balance.getLot();
        return new StockBalanceResponse(
                balance.getBalanceId(),
                balance.getItem().getItemId(),
                balance.getWarehouse().getWarehouseId(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                balance.getQuantity(),
                balance.getReservedQuantity(),
                balance.getQualityHoldQuantity(),
                issuableQuantity(balance),
                balance.getUpdatedAt());
    }

    /**
     * What a client may actually take out of this row — {@code 0} for a lot that is not
     * {@code AVAILABLE}, because B3 forbids issuing or reserving it.
     * <p>
     * {@link StockBalance#availableQuantity()} is row arithmetic only: it cannot see
     * {@code lot.status}, so on its own it reports HOLD/REJECTED stock as available and contradicts
     * the aggregate queries ({@code StockBalanceRepository.aggregate*}), which do filter by lot
     * status. The domain method is deliberately left alone — three stock-mutation gates
     * ({@code InventoryMovementService}, {@code WorkOrderExecutionSupport},
     * {@code MaterialReservationService}) call it <em>after</em> validating lot status themselves,
     * and making it lot-aware would break the QC path that adjusts stock out of a REJECTED lot
     * (CLAUDE.md §0.13).
     * <p>
     * The held quantity is <b>not</b> folded into {@code qualityHoldQuantity}: that column is the
     * QC carrier for stock with <em>no</em> lot row (B2, V56). For lot-tracked stock the reason is
     * carried by {@code lot.status}, which every response here already exposes.
     */
    private BigDecimal issuableQuantity(StockBalance balance) {
        InventoryLot lot = balance.getLot();
        if (lot != null && lot.getStatus() != LotStatus.AVAILABLE) {
            return BigDecimal.ZERO;
        }
        return balance.availableQuantity();
    }

    public StockMovementResponse toResponse(StockMovement movement) {
        InventoryLot lot = movement.getLot();
        return new StockMovementResponse(
                movement.getMovementId(),
                movement.getItem().getItemId(),
                movement.getWarehouse().getWarehouseId(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                movement.getMovementType().name(),
                movement.getDirection().name(),
                movement.getQuantity(),
                movement.getReason(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getIdempotencyKey(),
                movement.getCreatedAt());
    }

    /**
     * Dashboard variant of {@link #toResponse(StockMovement)}: resolves the item/warehouse labels the
     * card shows. {@code item} and {@code warehouse} must already be fetched — the only caller reads
     * the row through {@code findRecentByWarehouseIds}, whose {@code join fetch} guarantees that.
     * {@code actorUsername} is resolved by the caller in one batch (rule C14) and may be null.
     */
    public DashboardRecentMovementResponse toDashboardMovementResponse(StockMovement movement,
                                                                       String actorUsername) {
        InventoryLot lot = movement.getLot();
        Item item = movement.getItem();
        Warehouse warehouse = movement.getWarehouse();
        return new DashboardRecentMovementResponse(
                movement.getMovementId(),
                movement.getMovementType().name(),
                movement.getDirection().name(),
                item.getItemId(),
                item.getCode(),
                item.getName(),
                item.getUnit(),
                warehouse.getWarehouseId(),
                warehouse.getCode(),
                warehouse.getName(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                movement.getQuantity(),
                movement.getReason(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getCreatedBy(),
                actorUsername,
                movement.getCreatedAt());
    }

    /**
     * List rows and the status-change response (C2-2). {@code balance} must have {@code lot}/
     * {@code item}/{@code warehouse} already resolved (non-null) — every caller in this module reads
     * it either via {@code searchLots}'s {@code join fetch} or after resolving it explicitly for the
     * status-change write path. {@code sourceMovement} may be {@code null} (no RECEIVE movement found
     * for the lot yet).
     */
    public InventoryLotResponse toLotResponse(StockBalance balance, StockMovement sourceMovement) {
        InventoryLot lot = balance.getLot();
        Item item = balance.getItem();
        Warehouse warehouse = balance.getWarehouse();
        return new InventoryLotResponse(
                lot.getLotId(),
                item.getItemId(),
                item.getCode(),
                item.getName(),
                warehouse.getWarehouseId(),
                warehouse.getCode(),
                warehouse.getName(),
                lot.getLotCode(),
                lot.getStatus().name(),
                balance.getQuantity(),
                balance.getReservedQuantity(),
                issuableQuantity(balance),
                lot.getReceivedAt(),
                lot.getReceivedAt(),
                lot.getExpiresAt(),
                sourceMovement != null ? sourceMovement.getMovementType().name() : null,
                sourceMovement != null ? sourceMovement.getReferenceType() : null,
                sourceMovement != null ? sourceMovement.getReferenceId() : null,
                sourceMovement != null ? sourceMovement.getCreatedAt() : null,
                lot.getVersion(),
                lot.getCreatedAt(),
                lot.getUpdatedAt());
    }

    public InventoryLotDetailResponse toLotDetailResponse(InventoryLot lot, List<StockBalance> balances,
                                                           StockMovement sourceMovement) {
        Item item = lot.getItem();
        return new InventoryLotDetailResponse(
                lot.getLotId(),
                item.getItemId(),
                item.getCode(),
                item.getName(),
                lot.getLotCode(),
                lot.getStatus().name(),
                lot.getReceivedAt(),
                lot.getReceivedAt(),
                lot.getExpiresAt(),
                sourceMovement != null ? sourceMovement.getMovementType().name() : null,
                sourceMovement != null ? sourceMovement.getReferenceType() : null,
                sourceMovement != null ? sourceMovement.getReferenceId() : null,
                sourceMovement != null ? sourceMovement.getCreatedAt() : null,
                lot.getVersion(),
                lot.getCreatedAt(),
                lot.getUpdatedAt(),
                balances.stream().map(this::toLotBalanceResponse).toList());
    }

    public InventoryLotBalanceResponse toLotBalanceResponse(StockBalance balance) {
        Warehouse warehouse = balance.getWarehouse();
        return new InventoryLotBalanceResponse(
                warehouse.getWarehouseId(),
                warehouse.getCode(),
                warehouse.getName(),
                balance.getQuantity(),
                balance.getReservedQuantity(),
                issuableQuantity(balance));
    }

    public ItemWarehouseSettingResponse toResponse(ItemWarehouseSetting setting) {
        return new ItemWarehouseSettingResponse(
                setting.getSettingId(),
                setting.getItem().getItemId(),
                setting.getItem().getCode(),
                setting.getItem().getName(),
                setting.getWarehouse().getWarehouseId(),
                setting.getWarehouse().getCode(),
                setting.getWarehouse().getName(),
                setting.getSafetyStock(),
                setting.getReorderPoint(),
                setting.getLeadTimeDays(),
                setting.isDefaultSupply(),
                setting.isDefaultOutput(),
                setting.getStatus().name(),
                setting.getCreatedAt(),
                setting.getUpdatedAt());
    }
}
