package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSetting;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSettingStatus;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.planning.domain.PlanningMessageCode;
import com.erp.manufacturing.module.planning.domain.WarehouseResolutionSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Deterministic DEC-03 warehouse policy. It never selects an arbitrary first row. */
@Service
@RequiredArgsConstructor
public class MrpWarehouseResolutionService {

    public enum Role { SUPPLY, OUTPUT }

    private final WarehouseRepository warehouseRepository;
    private final ItemWarehouseSettingRepository settingRepository;

    public Resolution resolve(Plant plant, Item item, Role role) {
        List<Warehouse> activeWarehouses = warehouseRepository.findByPlantPlantId(plant.getPlantId()).stream()
                .filter(Warehouse::isActive)
                .toList();
        if (activeWarehouses.size() == 1) {
            return Resolution.resolved(activeWarehouses.get(0), WarehouseResolutionSource.SINGLE_ACTIVE_WAREHOUSE);
        }

        List<ItemWarehouseSetting> settings = settingRepository
                .findByItemItemIdAndWarehousePlantPlantIdAndStatus(
                        item.getItemId(), plant.getPlantId(), ItemWarehouseSettingStatus.ACTIVE)
                .stream()
                .filter(setting -> setting.getWarehouse().isActive())
                .toList();
        List<ItemWarehouseSetting> defaults = settings.stream()
                .filter(setting -> role == Role.SUPPLY ? setting.isDefaultSupply() : setting.isDefaultOutput())
                .toList();
        if (defaults.size() == 1) {
            return Resolution.resolved(defaults.get(0).getWarehouse(), WarehouseResolutionSource.ITEM_WAREHOUSE_DEFAULT);
        }
        if (defaults.size() > 1 || settings.size() > 1) {
            return Resolution.blocked(PlanningMessageCode.AMBIGUOUS_WAREHOUSE_POLICY);
        }
        if (settings.size() == 1) {
            return Resolution.resolved(settings.get(0).getWarehouse(), WarehouseResolutionSource.ITEM_WAREHOUSE_ONLY);
        }

        WarehouseType expectedType = expectedType(item.getType());
        List<Warehouse> typeMatches = activeWarehouses.stream()
                .filter(warehouse -> warehouse.getType() == expectedType)
                .toList();
        if (typeMatches.size() == 1) {
            return Resolution.resolved(typeMatches.get(0), WarehouseResolutionSource.WAREHOUSE_TYPE_FALLBACK);
        }
        if (typeMatches.size() > 1) {
            return Resolution.blocked(PlanningMessageCode.AMBIGUOUS_WAREHOUSE_POLICY);
        }
        return Resolution.blocked(PlanningMessageCode.MISSING_WAREHOUSE_POLICY);
    }

    private WarehouseType expectedType(ItemType itemType) {
        return switch (itemType) {
            case RAW_MATERIAL, CONSUMABLE -> WarehouseType.RAW_MATERIAL;
            case WIP -> WarehouseType.WIP;
            case FINISHED_GOOD -> WarehouseType.FINISHED_GOODS;
            case SERVICE -> WarehouseType.GENERAL;
        };
    }

    public record Resolution(Warehouse warehouse,
                             WarehouseResolutionSource source,
                             PlanningMessageCode blockingMessage) {
        static Resolution resolved(Warehouse warehouse, WarehouseResolutionSource source) {
            return new Resolution(warehouse, source, null);
        }

        static Resolution blocked(PlanningMessageCode message) {
            return new Resolution(null, WarehouseResolutionSource.UNRESOLVED, message);
        }

        public boolean isBlocked() {
            return blockingMessage != null;
        }

        public boolean usedFallback() {
            return source == WarehouseResolutionSource.SINGLE_ACTIVE_WAREHOUSE
                    || source == WarehouseResolutionSource.WAREHOUSE_TYPE_FALLBACK;
        }
    }
}
