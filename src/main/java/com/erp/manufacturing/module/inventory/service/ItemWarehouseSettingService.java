package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingRequest;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.organization.domain.Warehouse;
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
    private final ItemRepository itemRepository;
    private final OrganizationLookupService organizationLookupService;
    private final PlantContextResolver plantContextResolver;
    private final InventoryMapper mapper;

    /**
     * Creates or updates the setting of one item in one warehouse.
     *
     * <p>{@code plantHeader} is the raw {@code X-Plant-Id} (G2-3). It is checked against the plant of
     * the target warehouse after that warehouse is loaded and before anything is written; absent means
     * no check, as on every other endpoint.
     *
     * <p>Order is load-bearing: validate, then lock the item row, then resolve the pair and the peers
     * holding default roles, then decide {@code replaceDefaults}, and only then mutate. The lock is
     * what serializes two concurrent upserts of the same item; reading peers before it would let both
     * see "nobody holds the role" and both claim it.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MANAGE', 'WAREHOUSE', #request.warehouseId())")
    public ItemWarehouseSettingResponse upsert(ItemWarehouseSettingRequest request, String plantHeader) {
        Item item = itemLookupService.getActiveItem(request.itemId());
        Warehouse warehouse = findActiveWarehouse(request.warehouseId());
        plantContextResolver.ensureMatches(plantHeader, warehouse.getPlant().getPlantId());
        ensureSameCompany(item, warehouse);
        validateThresholds(request.safetyStock(), request.reorderPoint(), request.leadTimeDays());

        itemRepository.lockByItemId(item.getItemId());

        ItemWarehouseSetting setting = findPair(item, warehouse);
        List<ItemWarehouseSetting> displaced = peersHoldingRequestedRoles(
                setting, warehouse, request.defaultSupply(), request.defaultOutput());
        if (!displaced.isEmpty() && Boolean.FALSE.equals(request.replaceDefaults())) {
            throw ExceptionFactory.custom(BusinessErrorCode.DEFAULT_CONFLICT,
                    "Default role already held by warehouse " + displaced.get(0).getWarehouse().getCode()
                            + "; resend with replaceDefaults=true to move it");
        }
        releaseDefaultRoles(displaced, request.defaultSupply(), request.defaultOutput());

        setting.setSafetyStock(request.safetyStock());
        setting.setReorderPoint(request.reorderPoint());
        setting.setLeadTimeDays(request.leadTimeDays());
        setting.setDefaultSupply(request.defaultSupply());
        setting.setDefaultOutput(request.defaultOutput());
        setting.activate();
        return mapper.toResponse(settingRepository.save(setting));
    }

    /**
     * {@code plantId} (G2-3) is the scope FE lists by. With it the guard is a PLANT check; the two older
     * branches (by warehouse, or unscoped for GLOBAL holders) are kept unchanged so no existing caller
     * starts failing.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("#plantId != null ? @permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'PLANT', #plantId) : (#warehouseId == null ? @permissionGuard.hasPermission(authentication, 'PERM_INVENTORY_READ') : @permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId))")
    public PageResult<ItemWarehouseSettingResponse> list(UUID plantId, UUID warehouseId, UUID itemId, Pageable pageable) {
        return PageResult.from(settingRepository.search(
                        plantId, warehouseId, itemId, ItemWarehouseSettingStatus.ACTIVE, pageable)
                .map(mapper::toResponse));
    }

    @Transactional
    @PreAuthorize("@inventorySettingPermissionGuard.hasSettingAccess(authentication, 'PERM_INVENTORY_MANAGE', #settingId)")
    public void deactivate(UUID settingId, String plantHeader) {
        ItemWarehouseSetting setting = settingRepository.findWithDetailsBySettingId(settingId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Item warehouse setting", settingId));
        plantContextResolver.ensureMatches(plantHeader, setting.getWarehouse().getPlant().getPlantId());
        setting.deactivate();
        settingRepository.save(setting);
    }

    private Warehouse findActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = organizationLookupService.findWarehouse(warehouseId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId));
        if (!warehouse.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive warehouse cannot be used for item warehouse setting: " + warehouseId);
        }
        if (!warehouse.getPlant().isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Warehouse belongs to an inactive plant and cannot be used for item warehouse setting: "
                            + warehouseId);
        }
        return warehouse;
    }

    /**
     * One row per pair (G2-3, FE Q7): the ACTIVE row if there is one, otherwise the most recently
     * touched INACTIVE row — reactivated in place so its {@code settingId} survives — and only when the
     * pair has never existed, a new row.
     */
    private ItemWarehouseSetting findPair(Item item, Warehouse warehouse) {
        return settingRepository.findByItemItemIdAndWarehouseWarehouseIdAndStatus(
                        item.getItemId(), warehouse.getWarehouseId(), ItemWarehouseSettingStatus.ACTIVE)
                .or(() -> settingRepository.findFirstByItemItemIdAndWarehouseWarehouseIdAndStatusOrderByUpdatedAtDesc(
                        item.getItemId(), warehouse.getWarehouseId(), ItemWarehouseSettingStatus.INACTIVE))
                .orElseGet(() -> ItemWarehouseSetting.builder()
                        .item(item)
                        .warehouse(warehouse)
                        .status(ItemWarehouseSettingStatus.ACTIVE)
                        .build());
    }

    private void ensureSameCompany(Item item, Warehouse warehouse) {
        UUID itemCompanyId = item.getCompany().getCompanyId();
        UUID warehouseCompanyId = warehouse.getPlant().getCompany().getCompanyId();
        if (!itemCompanyId.equals(warehouseCompanyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
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

    /** Other ACTIVE settings of the same item and plant that hold a role this request claims. */
    private List<ItemWarehouseSetting> peersHoldingRequestedRoles(ItemWarehouseSetting current,
                                                                  Warehouse warehouse,
                                                                  boolean defaultSupply,
                                                                  boolean defaultOutput) {
        if (!defaultSupply && !defaultOutput) {
            return List.of();
        }
        return settingRepository
                .findByItemItemIdAndWarehousePlantPlantIdAndStatus(
                        current.getItem().getItemId(), warehouse.getPlant().getPlantId(),
                        ItemWarehouseSettingStatus.ACTIVE)
                .stream()
                .filter(setting -> current.getSettingId() == null
                        || !current.getSettingId().equals(setting.getSettingId()))
                .filter(setting -> (defaultSupply && setting.isDefaultSupply())
                        || (defaultOutput && setting.isDefaultOutput()))
                .toList();
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
     *
     * <p>Only the roles this request claims are released; a peer's other role is never touched.
     */
    private void releaseDefaultRoles(List<ItemWarehouseSetting> displaced, boolean defaultSupply, boolean defaultOutput) {
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
