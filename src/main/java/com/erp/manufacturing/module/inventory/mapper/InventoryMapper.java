package com.erp.manufacturing.module.inventory.mapper;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.*;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import org.springframework.stereotype.Component;

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
                balance.availableQuantity(),
                balance.getUpdatedAt());
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
                balance.availableQuantity(),
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
                balance.availableQuantity());
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
                setting.getStatus().name(),
                setting.getCreatedAt(),
                setting.getUpdatedAt());
    }
}
