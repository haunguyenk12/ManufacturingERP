package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingRequest;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemWarehouseSettingService {

    private final ItemWarehouseSettingRepository settingRepository;
    private final ItemLookupService itemLookupService;
    private final WarehouseRepository warehouseRepository;
    private final InventoryMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MANAGE', 'WAREHOUSE', #request.warehouseId())")
    public ItemWarehouseSettingResponse upsert(ItemWarehouseSettingRequest request) {
        Item item = itemLookupService.getActiveItem(request.itemId());
        Warehouse warehouse = findActiveWarehouse(request.warehouseId());
        ensureSameCompany(item, warehouse);
        validateThresholds(request.safetyStock(), request.reorderPoint(), request.leadTimeDays());

        ItemWarehouseSetting setting = settingRepository.findByItemItemIdAndWarehouseWarehouseIdAndStatus(
                        item.getItemId(), warehouse.getWarehouseId(), ItemWarehouseSettingStatus.ACTIVE)
                .orElseGet(() -> ItemWarehouseSetting.builder()
                        .item(item)
                        .warehouse(warehouse)
                        .status(ItemWarehouseSettingStatus.ACTIVE)
                        .build());

        setting.setSafetyStock(request.safetyStock());
        setting.setReorderPoint(request.reorderPoint());
        setting.setLeadTimeDays(request.leadTimeDays());
        releaseDefaultRoleFromOtherWarehouses(setting, warehouse, request.defaultSupply(), request.defaultOutput());
        setting.setDefaultSupply(request.defaultSupply());
        setting.setDefaultOutput(request.defaultOutput());
        setting.activate();
        return mapper.toResponse(settingRepository.save(setting));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("#warehouseId == null ? @permissionGuard.hasPermission(authentication, 'PERM_INVENTORY_READ') : @permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId)")
    public PageResult<ItemWarehouseSettingResponse> list(UUID warehouseId, UUID itemId, Pageable pageable) {
        return PageResult.from(settingRepository.search(
                        warehouseId, itemId, ItemWarehouseSettingStatus.ACTIVE, pageable)
                .map(mapper::toResponse));
    }

    @Transactional
    @PreAuthorize("@inventorySettingPermissionGuard.hasSettingAccess(authentication, 'PERM_INVENTORY_MANAGE', #settingId)")
    public void deactivate(UUID settingId) {
        ItemWarehouseSetting setting = settingRepository.findWithDetailsBySettingId(settingId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Item warehouse setting", settingId));
        setting.deactivate();
        settingRepository.save(setting);
    }

    private Warehouse findActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId));
        if (!warehouse.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Inactive warehouse cannot be used for item warehouse setting: " + warehouseId);
        }
        return warehouse;
    }

    private void ensureSameCompany(Item item, Warehouse warehouse) {
        UUID itemCompanyId = item.getCompany().getCompanyId();
        UUID warehouseCompanyId = warehouse.getPlant().getCompany().getCompanyId();
        if (!itemCompanyId.equals(warehouseCompanyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item and warehouse must belong to the same company");
        }
    }

    private void validateThresholds(BigDecimal safetyStock, BigDecimal reorderPoint, Integer leadTimeDays) {
        if (isNegative(safetyStock) || isNegative(reorderPoint) || leadTimeDays == null || leadTimeDays < 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BUSINESS_RULE_VIOLATION,
                    "Safety stock, reorder point, and lead time must be non-negative");
        }
    }

    private boolean isNegative(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Moves a default role onto this warehouse by taking it off whichever warehouse held it, in the
     * same transaction.
     *
     * <p>Refusing the write instead (the earlier behaviour) forced the caller into two calls — clear
     * the old default, then set the new one — with no transaction spanning them. A failure between
     * the two leaves the plant with <b>no</b> default for that role, which is worse than either end
     * state: MRP then reports {@code AMBIGUOUS_WAREHOUSE_POLICY} and blocks every suggestion for the
     * item until someone notices. One call that moves the flag cannot land in that hole.
     *
     * <p>The flush is load-bearing, not tidiness. {@code trg_item_warehouse_default_role} (V66)
     * rejects a row claiming a role another ACTIVE row already holds, and Hibernate is free to order
     * this UPDATE after the caller's. Releasing the old holders first and flushing before the caller
     * writes keeps the database from ever seeing two claimants — the same ordering lesson as the
     * Sales Order line replacement in CLAUDE.md §0.40.
     */
    private void releaseDefaultRoleFromOtherWarehouses(ItemWarehouseSetting current,
                                                       Warehouse warehouse,
                                                       boolean defaultSupply,
                                                       boolean defaultOutput) {
        if (!defaultSupply && !defaultOutput) {
            return;
        }
        List<ItemWarehouseSetting> displaced = settingRepository
                .findByItemItemIdAndWarehousePlantPlantIdAndStatus(
                        current.getItem().getItemId(), warehouse.getPlant().getPlantId(),
                        ItemWarehouseSettingStatus.ACTIVE)
                .stream()
                .filter(setting -> current.getSettingId() == null
                        || !current.getSettingId().equals(setting.getSettingId()))
                .filter(setting -> (defaultSupply && setting.isDefaultSupply())
                        || (defaultOutput && setting.isDefaultOutput()))
                .toList();
        if (displaced.isEmpty()) {
            return;
        }
        for (ItemWarehouseSetting setting : displaced) {
            if (defaultSupply) {
                setting.setDefaultSupply(false);
            }
            if (defaultOutput) {
                setting.setDefaultOutput(false);
            }
        }
        settingRepository.saveAllAndFlush(displaced);
    }
}
