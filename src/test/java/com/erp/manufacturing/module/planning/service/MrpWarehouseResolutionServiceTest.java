package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSetting;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSettingStatus;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.planning.domain.PlanningMessageCode;
import com.erp.manufacturing.module.planning.domain.WarehouseResolutionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * DEC-03 precedence ladder (plan BE-3B). Every rung that resolves has a blocked counterpart here on
 * purpose: the whole value of the policy is that it never falls back to "first row wins", and a
 * suite that only proved the happy rungs would still pass if the ambiguous branches quietly picked
 * a warehouse.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MrpWarehouseResolutionService (DEC-03) tests")
class MrpWarehouseResolutionServiceTest {

    @Mock WarehouseRepository warehouseRepository;
    @Mock ItemWarehouseSettingRepository settingRepository;

    MrpWarehouseResolutionService service;

    Plant plant;
    Item rawMaterial;

    @BeforeEach
    void setUp() {
        service = new MrpWarehouseResolutionService(warehouseRepository, settingRepository);
        plant = Plant.builder().plantId(UUID.randomUUID()).code("PLANT-A").build();
        rawMaterial = item(ItemType.RAW_MATERIAL);
    }

    @Test
    @DisplayName("Rung 4: a plant with exactly one active warehouse needs no policy at all")
    void resolve_singleActiveWarehouse_usesItForEveryItem() {
        Warehouse only = warehouse(WarehouseType.FINISHED_GOODS, OrganizationStatus.ACTIVE);
        activeWarehouses(only);

        var resolution = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);

        assertThat(resolution.isBlocked()).isFalse();
        assertThat(resolution.warehouse()).isSameAs(only);
        assertThat(resolution.source()).isEqualTo(WarehouseResolutionSource.SINGLE_ACTIVE_WAREHOUSE);
        assertThat(resolution.usedFallback()).isTrue();
    }

    /** A deactivated second warehouse must not make an unambiguous plant look ambiguous. */
    @Test
    void resolve_inactiveWarehousesAreNotCandidates() {
        Warehouse active = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE);
        Warehouse retired = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.INACTIVE);
        activeWarehouses(active, retired);

        var resolution = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);

        assertThat(resolution.warehouse()).isSameAs(active);
        assertThat(resolution.source()).isEqualTo(WarehouseResolutionSource.SINGLE_ACTIVE_WAREHOUSE);
    }

    @Test
    @DisplayName("Rung 5: the explicit default for the requested role beats the other candidates")
    void resolve_explicitDefaultForRole_wins() {
        Warehouse defaultSupply = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE);
        Warehouse other = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE);
        activeWarehouses(defaultSupply, other);
        settings(setting(defaultSupply, true, false), setting(other, false, false));

        var resolution = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);

        assertThat(resolution.warehouse()).isSameAs(defaultSupply);
        assertThat(resolution.source()).isEqualTo(WarehouseResolutionSource.ITEM_WAREHOUSE_DEFAULT);
        assertThat(resolution.usedFallback()).isFalse();
    }

    /**
     * SUPPLY and OUTPUT are separate roles on purpose. A warehouse flagged only as the output
     * warehouse must not be reused as the component source, otherwise the two flags collapse into
     * one default and DEC-03 stops distinguishing the two directions of stock movement.
     */
    @Test
    void resolve_defaultOutputDoesNotSatisfyTheSupplyRole() {
        Warehouse outputOnly = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE);
        Warehouse other = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE);
        activeWarehouses(outputOnly, other);
        settings(setting(outputOnly, false, true), setting(other, false, false));

        var supply = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);
        var output = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.OUTPUT);

        assertThat(supply.isBlocked()).isTrue();
        assertThat(supply.blockingMessage()).isEqualTo(PlanningMessageCode.AMBIGUOUS_WAREHOUSE_POLICY);
        assertThat(output.warehouse()).isSameAs(outputOnly);
        assertThat(output.source()).isEqualTo(WarehouseResolutionSource.ITEM_WAREHOUSE_DEFAULT);
    }

    @Test
    @DisplayName("Rung 5 blocked: two candidates and no default is AMBIGUOUS, never first-row-wins")
    void resolve_multipleSettingsWithoutDefault_isBlockedAmbiguous() {
        Warehouse first = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE);
        Warehouse second = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE);
        activeWarehouses(first, second);
        settings(setting(first, false, false), setting(second, false, false));

        var resolution = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);

        assertThat(resolution.isBlocked()).isTrue();
        assertThat(resolution.warehouse()).isNull();
        assertThat(resolution.blockingMessage()).isEqualTo(PlanningMessageCode.AMBIGUOUS_WAREHOUSE_POLICY);
        assertThat(resolution.source()).isEqualTo(WarehouseResolutionSource.UNRESOLVED);
    }

    @Test
    void resolve_exactlyOneSetting_needsNoDefaultFlag() {
        Warehouse configured = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE);
        activeWarehouses(configured, warehouse(WarehouseType.FINISHED_GOODS, OrganizationStatus.ACTIVE));
        settings(setting(configured, false, false));

        var resolution = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);

        assertThat(resolution.warehouse()).isSameAs(configured);
        assertThat(resolution.source()).isEqualTo(WarehouseResolutionSource.ITEM_WAREHOUSE_ONLY);
    }

    @Test
    @DisplayName("Rung 6: with no setting, the single warehouse of the item's own type is used")
    void resolve_warehouseTypeFallback_followsItemType() {
        Warehouse raw = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE);
        Warehouse finished = warehouse(WarehouseType.FINISHED_GOODS, OrganizationStatus.ACTIVE);
        activeWarehouses(raw, finished);
        settings();

        var forRawMaterial = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);
        var forFinishedGood = service.resolve(plant, item(ItemType.FINISHED_GOOD),
                MrpWarehouseResolutionService.Role.OUTPUT);

        assertThat(forRawMaterial.warehouse()).isSameAs(raw);
        assertThat(forRawMaterial.source()).isEqualTo(WarehouseResolutionSource.WAREHOUSE_TYPE_FALLBACK);
        assertThat(forRawMaterial.usedFallback()).isTrue();
        assertThat(forFinishedGood.warehouse()).isSameAs(finished);
    }

    @Test
    void resolve_twoWarehousesOfTheMatchingType_isBlockedAmbiguous() {
        activeWarehouses(warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE),
                warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE),
                warehouse(WarehouseType.FINISHED_GOODS, OrganizationStatus.ACTIVE));
        settings();

        var resolution = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);

        assertThat(resolution.isBlocked()).isTrue();
        assertThat(resolution.blockingMessage()).isEqualTo(PlanningMessageCode.AMBIGUOUS_WAREHOUSE_POLICY);
    }

    @Test
    @DisplayName("Rung 7: no candidate at all is MISSING, a different diagnosis from AMBIGUOUS")
    void resolve_noMatchingWarehouse_isBlockedMissingPolicy() {
        activeWarehouses(warehouse(WarehouseType.FINISHED_GOODS, OrganizationStatus.ACTIVE),
                warehouse(WarehouseType.SCRAP, OrganizationStatus.ACTIVE));
        settings();

        var resolution = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);

        assertThat(resolution.isBlocked()).isTrue();
        assertThat(resolution.blockingMessage()).isEqualTo(PlanningMessageCode.MISSING_WAREHOUSE_POLICY);
        assertThat(resolution.source()).isEqualTo(WarehouseResolutionSource.UNRESOLVED);
    }

    /** A default pointing at a deactivated warehouse must not resolve to it. */
    @Test
    void resolve_settingOnAnInactiveWarehouse_isIgnored() {
        Warehouse retired = warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.INACTIVE);
        activeWarehouses(warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE),
                warehouse(WarehouseType.RAW_MATERIAL, OrganizationStatus.ACTIVE),
                retired);
        settings(setting(retired, true, true));

        var resolution = service.resolve(plant, rawMaterial, MrpWarehouseResolutionService.Role.SUPPLY);

        assertThat(resolution.isBlocked()).isTrue();
        assertThat(resolution.blockingMessage()).isEqualTo(PlanningMessageCode.AMBIGUOUS_WAREHOUSE_POLICY);
    }

    private void activeWarehouses(Warehouse... warehouses) {
        when(warehouseRepository.findByPlantPlantId(plant.getPlantId())).thenReturn(List.of(warehouses));
    }

    private void settings(ItemWarehouseSetting... settings) {
        lenient().when(settingRepository.findByItemItemIdAndWarehousePlantPlantIdAndStatus(
                        any(), any(), any()))
                .thenReturn(List.of(settings));
    }

    private ItemWarehouseSetting setting(Warehouse warehouse, boolean defaultSupply, boolean defaultOutput) {
        return ItemWarehouseSetting.builder()
                .settingId(UUID.randomUUID())
                .item(rawMaterial)
                .warehouse(warehouse)
                .defaultSupply(defaultSupply)
                .defaultOutput(defaultOutput)
                .status(ItemWarehouseSettingStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(WarehouseType type, OrganizationStatus status) {
        return Warehouse.builder()
                .warehouseId(UUID.randomUUID())
                .plant(plant)
                .code("WH-" + type.name() + "-" + UUID.randomUUID())
                .name(type.name())
                .type(type)
                .status(status)
                .build();
    }

    private Item item(ItemType type) {
        return Item.builder().itemId(UUID.randomUUID()).code("ITEM-" + type.name()).type(type).build();
    }
}
