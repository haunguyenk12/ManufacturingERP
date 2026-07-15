package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSettingStatus;
import com.erp.manufacturing.module.inventory.repository.ItemWarehousePlanningSettingProjection;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.inventory.repository.StockAvailabilityProjection;
import com.erp.manufacturing.module.inventory.repository.StockAvailabilityByWarehouseProjection;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockPlanningQuantityProjection;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAvailabilityService tests")
class InventoryAvailabilityServiceTest {

    @Mock StockBalanceRepository stockBalanceRepository;
    @Mock ItemWarehouseSettingRepository itemWarehouseSettingRepository;

    InventoryAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new InventoryAvailabilityService(stockBalanceRepository, itemWarehouseSettingRepository);
    }

    @Test
    void getAvailableQuantities_emptyInputs_returnEmptyWithoutRepositoryCall() {
        assertThat(service.getAvailableQuantities(List.of(), List.of(UUID.randomUUID()))).isEmpty();
        assertThat(service.getAvailableQuantities(List.of(UUID.randomUUID()), List.of())).isEmpty();

        verifyNoInteractions(stockBalanceRepository);
    }

    @Test
    void getAvailableQuantities_aggregatesAvailableLotAndNonLotBalances() {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(stockBalanceRepository.aggregateAvailableQuantities(
                List.of(itemId), List.of(warehouseId), LotStatus.AVAILABLE))
                .thenReturn(List.of(new TestProjection(itemId, new BigDecimal("12.500"))));

        Map<UUID, BigDecimal> result = service.getAvailableQuantities(List.of(itemId), List.of(warehouseId));

        assertThat(result).containsEntry(itemId, new BigDecimal("12.500"));
        verify(stockBalanceRepository).aggregateAvailableQuantities(
                List.of(itemId), List.of(warehouseId), LotStatus.AVAILABLE);
    }

    @Test
    void getAvailableQuantitiesByWarehouse_usesAvailableLotStatus() {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(stockBalanceRepository.aggregateAvailableQuantitiesByWarehouse(
                List.of(itemId), List.of(warehouseId), LotStatus.AVAILABLE))
                .thenReturn(List.of(new TestWarehouseProjection(itemId, warehouseId, new BigDecimal("8"))));

        Map<InventoryAvailabilityService.ItemWarehouseAvailabilityKey, BigDecimal> result =
                service.getAvailableQuantitiesByWarehouse(List.of(itemId), List.of(warehouseId));

        assertThat(result).containsEntry(
                new InventoryAvailabilityService.ItemWarehouseAvailabilityKey(itemId, warehouseId),
                new BigDecimal("8"));
        verify(stockBalanceRepository).aggregateAvailableQuantitiesByWarehouse(
                List.of(itemId), List.of(warehouseId), LotStatus.AVAILABLE);
    }

    @Test
    void getPlanningQuantities_mergesReservedAwareAvailabilityAndSettings() {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(stockBalanceRepository.aggregatePlanningQuantities(
                List.of(itemId), List.of(warehouseId), LotStatus.AVAILABLE))
                .thenReturn(List.of(new TestPlanningQuantityProjection(
                        itemId,
                        new BigDecimal("20"),
                        new BigDecimal("7"),
                        new BigDecimal("13"))));
        when(itemWarehouseSettingRepository.aggregatePlanningSettings(
                List.of(itemId), List.of(warehouseId), ItemWarehouseSettingStatus.ACTIVE))
                .thenReturn(List.of(new TestPlanningSettingProjection(
                        itemId,
                        new BigDecimal("5"),
                        new BigDecimal("8"),
                        3)));

        Map<UUID, InventoryAvailabilityService.PlanningInventoryQuantity> result =
                service.getPlanningQuantities(List.of(itemId), List.of(warehouseId));

        InventoryAvailabilityService.PlanningInventoryQuantity quantity = result.get(itemId);
        assertThat(quantity.onHandQuantity()).isEqualByComparingTo("20");
        assertThat(quantity.reservedQuantity()).isEqualByComparingTo("7");
        assertThat(quantity.availableQuantity()).isEqualByComparingTo("13");
        assertThat(quantity.safetyStockQuantity()).isEqualByComparingTo("5");
        assertThat(quantity.reorderPointQuantity()).isEqualByComparingTo("8");
        assertThat(quantity.leadTimeDays()).isEqualTo(3);
    }

    private record TestProjection(UUID itemId, BigDecimal quantity) implements StockAvailabilityProjection {
        @Override public UUID getItemId() { return itemId; }
        @Override public BigDecimal getQuantity() { return quantity; }
    }

    private record TestWarehouseProjection(UUID itemId, UUID warehouseId, BigDecimal quantity)
            implements StockAvailabilityByWarehouseProjection {
        @Override public UUID getItemId() { return itemId; }
        @Override public UUID getWarehouseId() { return warehouseId; }
        @Override public BigDecimal getQuantity() { return quantity; }
    }

    private record TestPlanningQuantityProjection(
            UUID itemId,
            BigDecimal onHandQuantity,
            BigDecimal reservedQuantity,
            BigDecimal availableQuantity
    ) implements StockPlanningQuantityProjection {
        @Override public UUID getItemId() { return itemId; }
        @Override public BigDecimal getOnHandQuantity() { return onHandQuantity; }
        @Override public BigDecimal getReservedQuantity() { return reservedQuantity; }
        @Override public BigDecimal getAvailableQuantity() { return availableQuantity; }
    }

    private record TestPlanningSettingProjection(
            UUID itemId,
            BigDecimal safetyStockQuantity,
            BigDecimal reorderPointQuantity,
            Integer leadTimeDays
    ) implements ItemWarehousePlanningSettingProjection {
        @Override public UUID getItemId() { return itemId; }
        @Override public BigDecimal getSafetyStockQuantity() { return safetyStockQuantity; }
        @Override public BigDecimal getReorderPointQuantity() { return reorderPointQuantity; }
        @Override public Integer getLeadTimeDays() { return leadTimeDays; }
    }
}
