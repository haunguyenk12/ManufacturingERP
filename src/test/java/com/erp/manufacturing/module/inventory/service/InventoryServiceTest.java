package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryMovementService tests")
class InventoryServiceTest {

    @Mock ItemRepository itemRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock InventoryLotRepository lotRepository;
    @Mock StockBalanceRepository balanceRepository;
    @Mock StockMovementRepository movementRepository;

    InventoryMovementService service;

    @BeforeEach
    void setUp() {
        service = new InventoryMovementService(
                itemRepository,
                warehouseRepository,
                lotRepository,
                balanceRepository,
                movementRepository);
    }

    @Test
    void receive_lotTrackedItem_createsLotAndIncreasesBalance() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        Item item = item(itemId, companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKey("KEY-1")).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(lotRepository.findByItemItemIdAndLotCode(itemId, "LOT-1")).thenReturn(Optional.empty());
        when(lotRepository.save(any(InventoryLot.class))).thenAnswer(invocation -> {
            InventoryLot lot = invocation.getArgument(0);
            lot.setLotId(lotId);
            return lot;
        });
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(itemId, warehouseId, lotId))
                .thenReturn(Optional.empty());
        when(balanceRepository.save(any(StockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, "LOT-1", new BigDecimal("10.500"), "PO receipt", "PO", "PO-1"), "KEY-1");

        ArgumentCaptor<StockBalance> balanceCaptor = ArgumentCaptor.forClass(StockBalance.class);
        verify(balanceRepository).save(balanceCaptor.capture());
        assertThat(balanceCaptor.getValue().getQuantity()).isEqualByComparingTo("10.500");

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getMovementType()).isEqualTo(MovementType.RECEIVE);
        assertThat(movementCaptor.getValue().getIdempotencyKey()).isEqualTo("KEY-1");
    }

    @Test
    void receive_duplicateIdempotencyKey_returnsOriginalMovementWithoutApplyingStock() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        StockMovement existing = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("KEY-1")
                .createdAt(Instant.now())
                .build();
        when(movementRepository.findByIdempotencyKey("KEY-1")).thenReturn(Optional.of(existing));

        service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null), "KEY-1");

        verifyNoInteractions(itemRepository, warehouseRepository, lotRepository, balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void issue_insufficientStock_failsAndDoesNotCreateMovement() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        StockBalance balance = StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .quantity(new BigDecimal("3"))
                .build();

        when(movementRepository.findByIdempotencyKey("KEY-2")).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.issue(new InventoryIssueCommand(
                itemId, warehouseId, null, null, new BigDecimal("5"), null, null, null), "KEY-2"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));

        verify(movementRepository, never()).save(any());
    }

    @Test
    void issue_reservedStockIsNotAvailableForUnreservedIssue() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        StockBalance balance = StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .quantity(new BigDecimal("10"))
                .reservedQuantity(new BigDecimal("8"))
                .build();

        when(movementRepository.findByIdempotencyKey("KEY-RESERVED")).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.issue(new InventoryIssueCommand(
                itemId, warehouseId, null, null, new BigDecimal("3"), null, null, null), "KEY-RESERVED"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));

        verify(movementRepository, never()).save(any());
    }

    @Test
    void issueReserved_decreasesQuantityAndReservedQuantity() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        StockBalance balance = StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .quantity(new BigDecimal("10"))
                .reservedQuantity(new BigDecimal("8"))
                .build();

        when(movementRepository.findByIdempotencyKey("KEY-RES-ISSUE")).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.of(balance));
        when(balanceRepository.save(balance)).thenReturn(balance);
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.issueReserved(new InventoryIssueCommand(
                itemId, warehouseId, null, null, new BigDecimal("5"), null, null, null), "KEY-RES-ISSUE");

        assertThat(balance.getQuantity()).isEqualByComparingTo("5");
        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("3");
    }

    @Test
    void issue_holdLot_failsBeforeStockMutation() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        Item item = item(itemId, companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        InventoryLot lot = InventoryLot.builder()
                .lotId(lotId)
                .item(item)
                .lotCode("LOT-HOLD")
                .status(LotStatus.HOLD)
                .build();

        when(movementRepository.findByIdempotencyKey("KEY-3")).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> service.issue(new InventoryIssueCommand(
                itemId, warehouseId, lotId, null, BigDecimal.ONE, null, null, null), "KEY-3"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void receive_itemAndWarehouseInDifferentCompanies_fails() {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, UUID.randomUUID(), false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, UUID.randomUUID(), OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKey("KEY-4")).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null), "KEY-4"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(balanceRepository);
    }

    private Item item(UUID itemId, UUID companyId, boolean lotTracked, ItemStatus status) {
        return Item.builder()
                .itemId(itemId)
                .company(company(companyId))
                .code("RM-001")
                .name("Steel Coil")
                .type(ItemType.RAW_MATERIAL)
                .unit("KG")
                .lotTracked(lotTracked)
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
