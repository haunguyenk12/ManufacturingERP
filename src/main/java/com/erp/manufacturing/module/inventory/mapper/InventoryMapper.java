package com.erp.manufacturing.module.inventory.mapper;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.*;
import org.springframework.stereotype.Component;

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
