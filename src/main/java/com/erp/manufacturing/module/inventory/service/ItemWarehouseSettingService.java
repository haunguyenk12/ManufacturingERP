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
}
