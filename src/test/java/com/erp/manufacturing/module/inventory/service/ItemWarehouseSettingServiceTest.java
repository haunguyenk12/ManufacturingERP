package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemWarehouseSettingService tests")
class ItemWarehouseSettingServiceTest {

    @Mock ItemWarehouseSettingRepository settingRepository;
    @Mock ItemLookupService itemLookupService;
    @Mock WarehouseRepository warehouseRepository;

    ItemWarehouseSettingService service;

    @BeforeEach
    void setUp() {
        service = new ItemWarehouseSettingService(
                settingRepository,
                itemLookupService,
                warehouseRepository,
                new InventoryMapper());
    }

    @Test
    void upsert_createsNewSetting() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        when(itemLookupService.getActiveItem(itemId)).thenReturn(item);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(settingRepository.findByItemItemIdAndWarehouseWarehouseIdAndStatus(
                itemId, warehouseId, ItemWarehouseSettingStatus.ACTIVE)).thenReturn(Optional.empty());
        when(settingRepository.save(any(ItemWarehouseSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(request(itemId, warehouseId, "10", "5", 7));

        ArgumentCaptor<ItemWarehouseSetting> captor = ArgumentCaptor.forClass(ItemWarehouseSetting.class);
        verify(settingRepository).save(captor.capture());
        assertThat(captor.getValue().getSafetyStock()).isEqualByComparingTo("10");
        assertThat(captor.getValue().getReorderPoint()).isEqualByComparingTo("5");
        assertThat(captor.getValue().getLeadTimeDays()).isEqualTo(7);
        assertThat(captor.getValue().getStatus()).isEqualTo(ItemWarehouseSettingStatus.ACTIVE);
    }

    @Test
    void upsert_updatesExistingActiveSettingInsteadOfCreatingDuplicate() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        ItemWarehouseSetting existing = setting(item, warehouse);
        when(itemLookupService.getActiveItem(itemId)).thenReturn(item);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(settingRepository.findByItemItemIdAndWarehouseWarehouseIdAndStatus(
                itemId, warehouseId, ItemWarehouseSettingStatus.ACTIVE)).thenReturn(Optional.of(existing));
        when(settingRepository.save(existing)).thenReturn(existing);

        service.upsert(request(itemId, warehouseId, "12", "6", 3));

        verify(settingRepository).save(existing);
        assertThat(existing.getSafetyStock()).isEqualByComparingTo("12");
        assertThat(existing.getReorderPoint()).isEqualByComparingTo("6");
        assertThat(existing.getLeadTimeDays()).isEqualTo(3);
    }

    @Test
    void upsert_itemAndWarehouseDifferentCompany_fails() {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(itemLookupService.getActiveItem(itemId)).thenReturn(item(itemId, UUID.randomUUID(), ItemStatus.ACTIVE));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(
                warehouse(warehouseId, UUID.randomUUID(), OrganizationStatus.ACTIVE)));

        assertThatThrownBy(() -> service.upsert(request(itemId, warehouseId, "10", "5", 1)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(settingRepository, never()).save(any());
    }

    @Test
    void upsert_inactiveWarehouse_fails() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(itemLookupService.getActiveItem(itemId)).thenReturn(item(itemId, companyId, ItemStatus.ACTIVE));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(
                warehouse(warehouseId, companyId, OrganizationStatus.INACTIVE)));

        assertThatThrownBy(() -> service.upsert(request(itemId, warehouseId, "10", "5", 1)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
    }

    @Test
    void upsert_negativeThresholds_fails() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(itemLookupService.getActiveItem(itemId)).thenReturn(item(itemId, companyId, ItemStatus.ACTIVE));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(
                warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE)));

        assertThatThrownBy(() -> service.upsert(request(itemId, warehouseId, "-1", "5", 1)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void deactivate_softDeletesSetting() {
        UUID settingId = UUID.randomUUID();
        ItemWarehouseSetting setting = setting(
                item(UUID.randomUUID(), UUID.randomUUID(), ItemStatus.ACTIVE),
                warehouse(UUID.randomUUID(), UUID.randomUUID(), OrganizationStatus.ACTIVE));
        when(settingRepository.findWithDetailsBySettingId(settingId)).thenReturn(Optional.of(setting));

        service.deactivate(settingId);

        assertThat(setting.getStatus()).isEqualTo(ItemWarehouseSettingStatus.INACTIVE);
        verify(settingRepository).save(setting);
    }

    // ── default supply/output roles (DEC-03) ──────────────────────────────

    /**
     * Moving a default must be one call, not "clear the old, then set the new". The two-call shape
     * has no transaction spanning it: fail between them and the plant is left with <b>no</b> default,
     * which makes MRP block every suggestion for the item with AMBIGUOUS_WAREHOUSE_POLICY. So the
     * previous holder is released here, and released <b>before</b> the caller's row is written —
     * the V66 trigger rejects two live claimants of the same role.
     */
    @Test
    void upsert_claimingADefaultRole_movesItOffThePreviousWarehouseInTheSameCall() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse target = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        Warehouse incumbentWarehouse = Warehouse.builder()
                .warehouseId(UUID.randomUUID())
                .plant(target.getPlant())
                .code("WH2").name("Warehouse 2")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
        ItemWarehouseSetting incumbent = setting(item, incumbentWarehouse);
        incumbent.setDefaultSupply(true);

        when(itemLookupService.getActiveItem(itemId)).thenReturn(item);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(target));
        when(settingRepository.findByItemItemIdAndWarehouseWarehouseIdAndStatus(
                itemId, warehouseId, ItemWarehouseSettingStatus.ACTIVE)).thenReturn(Optional.empty());
        when(settingRepository.findByItemItemIdAndWarehousePlantPlantIdAndStatus(
                itemId, target.getPlant().getPlantId(), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(incumbent));
        when(settingRepository.save(any(ItemWarehouseSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(new ItemWarehouseSettingRequest(itemId, warehouseId,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, true, false));

        assertThat(incumbent.isDefaultSupply()).isFalse();
        ArgumentCaptor<ItemWarehouseSetting> captor = ArgumentCaptor.forClass(ItemWarehouseSetting.class);
        verify(settingRepository).save(captor.capture());
        assertThat(captor.getValue().isDefaultSupply()).isTrue();
        // The release has to reach the database before the claim does, or the trigger sees two.
        InOrder inOrder = inOrder(settingRepository);
        inOrder.verify(settingRepository).saveAllAndFlush(List.of(incumbent));
        inOrder.verify(settingRepository).save(any(ItemWarehouseSetting.class));
    }

    /** Releasing SUPPLY must not quietly strip the OUTPUT role the same warehouse also holds. */
    @Test
    void upsert_claimingOneRole_leavesTheOtherRoleOfThePreviousWarehouseAlone() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse target = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        Warehouse incumbentWarehouse = Warehouse.builder()
                .warehouseId(UUID.randomUUID())
                .plant(target.getPlant())
                .code("WH2").name("Warehouse 2")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
        ItemWarehouseSetting incumbent = setting(item, incumbentWarehouse);
        incumbent.setDefaultSupply(true);
        incumbent.setDefaultOutput(true);

        when(itemLookupService.getActiveItem(itemId)).thenReturn(item);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(target));
        when(settingRepository.findByItemItemIdAndWarehouseWarehouseIdAndStatus(
                itemId, warehouseId, ItemWarehouseSettingStatus.ACTIVE)).thenReturn(Optional.empty());
        when(settingRepository.findByItemItemIdAndWarehousePlantPlantIdAndStatus(
                itemId, target.getPlant().getPlantId(), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(incumbent));
        when(settingRepository.save(any(ItemWarehouseSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(new ItemWarehouseSettingRequest(itemId, warehouseId,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, true, false));

        assertThat(incumbent.isDefaultSupply()).isFalse();
        assertThat(incumbent.isDefaultOutput()).isTrue();
    }

    /** A plain threshold edit must not go hunting for defaults to release. */
    @Test
    void upsert_claimingNoDefaultRole_neverTouchesTheOtherWarehouses() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(itemLookupService.getActiveItem(itemId)).thenReturn(item);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(settingRepository.findByItemItemIdAndWarehouseWarehouseIdAndStatus(
                itemId, warehouseId, ItemWarehouseSettingStatus.ACTIVE)).thenReturn(Optional.empty());
        when(settingRepository.save(any(ItemWarehouseSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(request(itemId, warehouseId, "10", "5", 7));

        verify(settingRepository, never()).findByItemItemIdAndWarehousePlantPlantIdAndStatus(any(), any(), any());
        verify(settingRepository, never()).saveAllAndFlush(any());
    }

    private ItemWarehouseSettingRequest request(UUID itemId, UUID warehouseId, String safety, String reorder, int leadTime) {
        return new ItemWarehouseSettingRequest(itemId, warehouseId, new BigDecimal(safety), new BigDecimal(reorder), leadTime);
    }

    private ItemWarehouseSetting setting(Item item, Warehouse warehouse) {
        return ItemWarehouseSetting.builder()
                .settingId(UUID.randomUUID())
                .item(item)
                .warehouse(warehouse)
                .safetyStock(BigDecimal.ZERO)
                .reorderPoint(BigDecimal.ZERO)
                .leadTimeDays(0)
                .status(ItemWarehouseSettingStatus.ACTIVE)
                .build();
    }

    private Item item(UUID itemId, UUID companyId, ItemStatus status) {
        return Item.builder()
                .itemId(itemId)
                .company(company(companyId))
                .code("RM-001")
                .name("Raw Material")
                .type(ItemType.RAW_MATERIAL)
                .unit("EA")
                .status(status)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, UUID companyId, OrganizationStatus status) {
        Plant plant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(company(companyId))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant)
                .code("WH1")
                .name("Warehouse 1")
                .type(WarehouseType.RAW_MATERIAL)
                .status(status)
                .build();
    }

    private Company company(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
