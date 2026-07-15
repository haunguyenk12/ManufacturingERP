package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.InventoryAlertLineResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAlertService tests")
class InventoryAlertServiceTest {

    @Mock ItemWarehouseSettingRepository settingRepository;
    @Mock InventoryAvailabilityService availabilityService;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock StockMovementRepository stockMovementRepository;

    InventoryAlertService service;

    @BeforeEach
    void setUp() {
        service = new InventoryAlertService(
                settingRepository,
                availabilityService,
                organizationLookupService,
                stockMovementRepository,
                new InventoryMapper());
    }

    @Test
    void listAlerts_availableBelowSafetyAboveReorder_returnsLowStock() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ItemWarehouseSetting setting = setting(companyId, warehouseId, itemId, "10", "5");
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(setting));
        when(availabilityService.getAvailableQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(key(itemId, warehouseId), new BigDecimal("7")));

        List<InventoryAlertLineResponse> result = service.listAlerts(
                ScopeResourceType.WAREHOUSE, warehouseId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(InventoryAlertStatus.LOW_STOCK.name());
    }

    @Test
    void listAlerts_availableAtReorderPoint_returnsReorderNeeded() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ItemWarehouseSetting setting = setting(companyId, warehouseId, itemId, "10", "5");
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(setting));
        when(availabilityService.getAvailableQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(key(itemId, warehouseId), new BigDecimal("5")));

        List<InventoryAlertLineResponse> result = service.listAlerts(
                ScopeResourceType.WAREHOUSE, warehouseId, InventoryAlertStatus.REORDER_NEEDED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(InventoryAlertStatus.REORDER_NEEDED.name());
    }

    @Test
    void listAlerts_missingSettings_returnsEmptyAndDoesNotReadAvailability() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(service.listAlerts(ScopeResourceType.WAREHOUSE, warehouseId, null)).isEmpty();

        verifyNoInteractions(availabilityService);
    }

    @Test
    void listAlerts_scopeUsesResolvedWarehouseIds() {
        UUID companyId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ItemWarehouseSetting setting = setting(companyId, warehouseId, itemId, "10", "5");
        when(organizationLookupService.resolveScope(ScopeResourceType.PLANT, scopeId))
                .thenReturn(scope(ScopeResourceType.PLANT, scopeId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(setting));
        when(availabilityService.getAvailableQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(key(itemId, warehouseId), new BigDecimal("20")));

        service.listAlerts(ScopeResourceType.PLANT, scopeId, null);

        verify(settingRepository).findByWarehouseWarehouseIdInAndStatus(
                List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE);
        verify(availabilityService).getAvailableQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId)));
    }

    @Test
    void getDashboard_countsStatusesAndLimitsActionableLines() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemA = UUID.randomUUID();
        UUID itemB = UUID.randomUUID();
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(
                        setting(companyId, warehouseId, itemA, "10", "5"),
                        setting(companyId, warehouseId, itemB, "10", "5")));
        when(availabilityService.getAvailableQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(
                        key(itemA, warehouseId), new BigDecimal("4"),
                        key(itemB, warehouseId), new BigDecimal("20")));
        when(stockMovementRepository.findRecentByWarehouseIds(anyCollection(), any())).thenReturn(List.of());

        InventoryDashboardResponse response = service.getDashboard(ScopeResourceType.WAREHOUSE, warehouseId);

        assertThat(response.totalItemCount()).isEqualTo(2);
        assertThat(response.totalWarehouseCount()).isEqualTo(1);
        assertThat(response.reorderNeededCount()).isEqualTo(1);
        assertThat(response.okCount()).isEqualTo(1);
        assertThat(response.shortageSummary().alertLineCount()).isEqualTo(1);
        assertThat(response.shortageSummary().safetyStockGapQuantity()).isEqualByComparingTo("6");
        assertThat(response.shortageSummary().reorderPointGapQuantity()).isEqualByComparingTo("1");
        assertThat(response.topLowStockLines()).hasSize(1);
    }

    private InventoryAvailabilityService.ItemWarehouseAvailabilityKey key(UUID itemId, UUID warehouseId) {
        return new InventoryAvailabilityService.ItemWarehouseAvailabilityKey(itemId, warehouseId);
    }

    private OrganizationScopeResolution scope(ScopeResourceType type, UUID scopeId, UUID companyId, List<UUID> warehouseIds) {
        return new OrganizationScopeResolution(type, scopeId, companyId, warehouseIds);
    }

    private ItemWarehouseSetting setting(UUID companyId, UUID warehouseId, UUID itemId, String safety, String reorder) {
        return ItemWarehouseSetting.builder()
                .settingId(UUID.randomUUID())
                .item(item(itemId, companyId))
                .warehouse(warehouse(warehouseId, companyId))
                .safetyStock(new BigDecimal(safety))
                .reorderPoint(new BigDecimal(reorder))
                .leadTimeDays(7)
                .status(ItemWarehouseSettingStatus.ACTIVE)
                .build();
    }

    private Item item(UUID itemId, UUID companyId) {
        return Item.builder()
                .itemId(itemId)
                .company(company(companyId))
                .code("ITEM-" + itemId.toString().substring(0, 4))
                .name("Item")
                .type(ItemType.RAW_MATERIAL)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, UUID companyId) {
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(Plant.builder()
                        .plantId(UUID.randomUUID())
                        .company(company(companyId))
                        .code("P1")
                        .name("Plant 1")
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .code("WH1")
                .name("Warehouse 1")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.ACTIVE)
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
