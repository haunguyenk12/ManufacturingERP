package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.DashboardRecentMovementResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryAlertLineResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.user.service.UserLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
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
    @Mock UserLookupService userLookupService;

    InventoryAlertService service;

    @BeforeEach
    void setUp() {
        service = new InventoryAlertService(
                settingRepository,
                availabilityService,
                organizationLookupService,
                stockMovementRepository,
                userLookupService,
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
        when(availabilityService.getStockQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(key(itemId, warehouseId), quantity("10", "3", "0", "7")));

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
        when(availabilityService.getStockQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(key(itemId, warehouseId), quantity("5", "0", "0", "5")));

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
        when(availabilityService.getStockQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(key(itemId, warehouseId), quantity("20", "0", "0", "20")));

        service.listAlerts(ScopeResourceType.PLANT, scopeId, null);

        verify(settingRepository).findByWarehouseWarehouseIdInAndStatus(
                List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE);
        verify(availabilityService).getStockQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId)));
    }

    /**
     * The FE dashboard shows a quantity next to a unit and an explanation of how availability was
     * reached; a line that only carries {@code availableQuantity} forces a per-row call to the item
     * API. {@code shortageQuantity} is the number the card leads with, so it is asserted as an exact
     * figure, not merely as non-null (rule R6).
     */
    @Test
    void listAlerts_lineCarriesTheUnitTheAvailabilityTermsAndTheShortage() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(setting(companyId, warehouseId, itemId, "20", "8")));
        when(availabilityService.getStockQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(key(itemId, warehouseId), quantity("12", "4", "1", "7")));

        InventoryAlertLineResponse line = service.listAlerts(ScopeResourceType.WAREHOUSE, warehouseId, null).get(0);

        assertThat(line.uomCode()).isEqualTo("EA");
        assertThat(line.onHandQuantity()).isEqualByComparingTo("12");
        assertThat(line.reservedQuantity()).isEqualByComparingTo("4");
        assertThat(line.qualityHoldQuantity()).isEqualByComparingTo("1");
        assertThat(line.availableQuantity()).isEqualByComparingTo("7");
        // Both thresholds must be cleared to reach OK: max(20, 8) − 7.
        assertThat(line.shortageQuantity()).isEqualByComparingTo("13");
    }

    @Test
    void listAlerts_okLine_reportsNoShortage() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(setting(companyId, warehouseId, itemId, "10", "5")));
        when(availabilityService.getStockQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(key(itemId, warehouseId), quantity("30", "0", "0", "30")));

        InventoryAlertLineResponse line = service.listAlerts(ScopeResourceType.WAREHOUSE, warehouseId, null).get(0);

        assertThat(line.status()).isEqualTo(InventoryAlertStatus.OK.name());
        assertThat(line.shortageQuantity()).isEqualByComparingTo("0");
    }

    /**
     * The same item in two warehouses is two independent lines with two independent statuses —
     * thresholds are configured per warehouse.
     */
    @Test
    void listAlerts_sameItemInTwoWarehouses_isEvaluatedIndependently() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseA = UUID.randomUUID();
        UUID warehouseB = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        List<UUID> warehouseIds = List.of(warehouseA, warehouseB);
        when(organizationLookupService.resolveScope(eq(ScopeResourceType.PLANT), any()))
                .thenReturn(scope(ScopeResourceType.PLANT, UUID.randomUUID(), companyId, warehouseIds));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(warehouseIds, ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(
                        setting(companyId, warehouseA, itemId, "10", "5"),
                        setting(companyId, warehouseB, itemId, "10", "5")));
        when(availabilityService.getStockQuantitiesByWarehouse(anyCollection(), eq(warehouseIds)))
                .thenReturn(Map.of(
                        key(itemId, warehouseA), quantity("2", "0", "0", "2"),
                        key(itemId, warehouseB), quantity("50", "0", "0", "50")));

        List<InventoryAlertLineResponse> result = service.listAlerts(
                ScopeResourceType.PLANT, UUID.randomUUID(), null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(InventoryAlertLineResponse::status)
                .containsExactly(InventoryAlertStatus.REORDER_NEEDED.name(), InventoryAlertStatus.OK.name());
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
        when(availabilityService.getStockQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(
                        key(itemA, warehouseId), quantity("4", "0", "0", "4"),
                        key(itemB, warehouseId), quantity("20", "0", "0", "20")));
        when(stockMovementRepository.findRecentByWarehouseIds(anyCollection(), any())).thenReturn(List.of());

        InventoryDashboardResponse response = service.getDashboard(
                ScopeResourceType.WAREHOUSE, warehouseId, null, null);

        assertThat(response.totalItemCount()).isEqualTo(2);
        assertThat(response.totalWarehouseCount()).isEqualTo(1);
        assertThat(response.reorderNeededCount()).isEqualTo(1);
        assertThat(response.okCount()).isEqualTo(1);
        assertThat(response.shortageSummary().alertLineCount()).isEqualTo(1);
        assertThat(response.shortageSummary().safetyStockGapQuantity()).isEqualByComparingTo("6");
        assertThat(response.shortageSummary().reorderPointGapQuantity()).isEqualByComparingTo("1");
        assertThat(response.topLowStockLines()).hasSize(1);
        assertThat(response.generatedAt()).isNotNull();
    }

    /** Worst first, so the FE can render the top N of a longer list without re-sorting. */
    @Test
    void getDashboard_ordersAlertLinesByStatusThenLargestShortage() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID mildReorder = UUID.randomUUID();
        UUID severeReorder = UUID.randomUUID();
        UUID lowStock = UUID.randomUUID();
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(
                        setting(companyId, warehouseId, mildReorder, "10", "5"),
                        setting(companyId, warehouseId, lowStock, "10", "5"),
                        setting(companyId, warehouseId, severeReorder, "10", "5")));
        when(availabilityService.getStockQuantitiesByWarehouse(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(
                        key(mildReorder, warehouseId), quantity("5", "0", "0", "5"),
                        key(lowStock, warehouseId), quantity("8", "0", "0", "8"),
                        key(severeReorder, warehouseId), quantity("0", "0", "0", "0")));
        when(stockMovementRepository.findRecentByWarehouseIds(anyCollection(), any())).thenReturn(List.of());

        InventoryDashboardResponse response = service.getDashboard(
                ScopeResourceType.WAREHOUSE, warehouseId, null, null);

        assertThat(response.topLowStockLines()).extracting(InventoryAlertLineResponse::itemId)
                .containsExactly(severeReorder, mildReorder, lowStock);
    }

    @Test
    void getDashboard_limitsAreClampedToTwentyAndDefaultToTen() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(anyCollection(), any())).thenReturn(List.of());
        when(stockMovementRepository.findRecentByWarehouseIds(anyCollection(), any())).thenReturn(List.of());

        service.getDashboard(ScopeResourceType.WAREHOUSE, warehouseId, null, null);
        service.getDashboard(ScopeResourceType.WAREHOUSE, warehouseId, 5, 5);
        service.getDashboard(ScopeResourceType.WAREHOUSE, warehouseId, 500, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(stockMovementRepository, times(3)).findRecentByWarehouseIds(anyCollection(), pageable.capture());
        assertThat(pageable.getAllValues()).extracting(Pageable::getPageSize).containsExactly(10, 5, 20);
    }

    /**
     * The movement card renders item/warehouse labels and the actor's username. Resolving the actor
     * must stay one batch query for the whole page (rule C14), and a row written outside a user
     * request (no {@code created_by}) must not break the lookup.
     */
    @Test
    void getDashboard_recentMovementsCarryLabelsAndResolveActorsInOneBatch() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, companyId);
        Item item = item(itemId, companyId);
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(settingRepository.findByWarehouseWarehouseIdInAndStatus(anyCollection(), any())).thenReturn(List.of());
        when(stockMovementRepository.findRecentByWarehouseIds(anyCollection(), any())).thenReturn(List.of(
                movement(item, warehouse, actorId),
                movement(item, warehouse, null)));
        when(userLookupService.findUsernames(anyCollection())).thenReturn(Map.of(actorId, "operator.a"));

        InventoryDashboardResponse response = service.getDashboard(
                ScopeResourceType.WAREHOUSE, warehouseId, null, null);

        assertThat(response.recentMovements()).hasSize(2);
        DashboardRecentMovementResponse first = response.recentMovements().get(0);
        assertThat(first.itemCode()).isEqualTo(item.getCode());
        assertThat(first.itemName()).isEqualTo("Item");
        assertThat(first.uomCode()).isEqualTo("EA");
        assertThat(first.warehouseCode()).isEqualTo("WH1");
        assertThat(first.warehouseName()).isEqualTo("Warehouse 1");
        assertThat(first.actorUserId()).isEqualTo(actorId);
        assertThat(first.actorUsername()).isEqualTo("operator.a");
        assertThat(response.recentMovements().get(1).actorUserId()).isNull();
        assertThat(response.recentMovements().get(1).actorUsername()).isNull();
        verify(userLookupService, times(1)).findUsernames(anyCollection());
    }

    @Test
    void getDashboard_emptyScope_returnsEmptyDashboardWithoutTouchingTheLedger() {
        UUID companyId = UUID.randomUUID();
        when(organizationLookupService.resolveScope(ScopeResourceType.COMPANY, companyId))
                .thenReturn(scope(ScopeResourceType.COMPANY, companyId, companyId, List.of()));

        InventoryDashboardResponse response = service.getDashboard(
                ScopeResourceType.COMPANY, companyId, null, null);

        assertThat(response.totalItemCount()).isZero();
        assertThat(response.topLowStockLines()).isEmpty();
        assertThat(response.recentMovements()).isEmpty();
        assertThat(response.generatedAt()).isNotNull();
        verifyNoInteractions(stockMovementRepository, userLookupService);
    }

    private InventoryAvailabilityService.ItemWarehouseAvailabilityKey key(UUID itemId, UUID warehouseId) {
        return new InventoryAvailabilityService.ItemWarehouseAvailabilityKey(itemId, warehouseId);
    }

    private InventoryAvailabilityService.WarehouseStockQuantity quantity(
            String onHand, String reserved, String qualityHold, String available) {
        return new InventoryAvailabilityService.WarehouseStockQuantity(
                new BigDecimal(onHand), new BigDecimal(reserved),
                new BigDecimal(qualityHold), new BigDecimal(available));
    }

    private OrganizationScopeResolution scope(ScopeResourceType type, UUID scopeId, UUID companyId, List<UUID> warehouseIds) {
        return new OrganizationScopeResolution(type, scopeId, companyId, warehouseIds);
    }

    private StockMovement movement(Item item, Warehouse warehouse, UUID actorId) {
        return StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.ISSUE)
                .direction(MovementDirection.OUT)
                .quantity(new BigDecimal("20"))
                .referenceType("WORK_ORDER")
                .referenceId(UUID.randomUUID().toString())
                .idempotencyKey(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .createdBy(actorId)
                .build();
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
