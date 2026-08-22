package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.InventoryLotDetailResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryLotResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryLotStatusChangeRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.workorder.service.query.LotQcOriginLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryLotService tests")
class InventoryLotServiceTest {

    @Mock InventoryLotRepository lotRepository;
    @Mock StockBalanceRepository balanceRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock InventoryMovementService movementService;
    @Mock LotQcOriginLookupService lotQcOriginLookupService;

    // R3: the mapper carries real mapping logic, so it is a real instance, not a mock.
    InventoryLotService service;

    private final UUID companyId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID warehouseId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InventoryLotService(
                lotRepository, balanceRepository, movementRepository, movementService,
                lotQcOriginLookupService, new InventoryMapper());
    }

    @Test
    @DisplayName("list: threads every filter through to the repository unchanged")
    void list_threadsFiltersThroughToTheRepository() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-06T00:00:00Z");
        when(balanceRepository.searchLots(
                eq(warehouseId), eq(itemId), eq(LotStatus.AVAILABLE), eq("LOT-"), eq(from), eq(to), any()))
                .thenReturn(Page.empty());

        service.list(warehouseId, itemId, LotStatus.AVAILABLE, "LOT-", from, to, PageRequest.of(0, 20));

        verify(balanceRepository).searchLots(
                eq(warehouseId), eq(itemId), eq(LotStatus.AVAILABLE), eq("LOT-"), eq(from), eq(to), any());
    }

    @Test
    @DisplayName("list: maps each row and resolves its origin RECEIVE movement")
    void list_mapsRowsAndResolvesOrigin() {
        StockBalance balance = balance(lot(LotStatus.AVAILABLE), new BigDecimal("10"), BigDecimal.ZERO);
        when(balanceRepository.searchLots(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(balance)));
        StockMovement origin = receiveMovement("WORK_ORDER", "wo-1");
        when(movementRepository.findFirstByLotLotIdAndMovementTypeOrderByCreatedAtAsc(lotId, MovementType.RECEIVE))
                .thenReturn(Optional.of(origin));

        var result = service.list(warehouseId, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        InventoryLotResponse response = result.content().get(0);
        assertThat(response.lotId()).isEqualTo(lotId);
        assertThat(response.sourceReferenceType()).isEqualTo("WORK_ORDER");
        assertThat(response.sourceReferenceId()).isEqualTo("wo-1");
    }

    /**
     * The frontend hit this on 2026-08-14: a lot on QC {@code HOLD} was listed with a positive
     * {@code availableQuantity}, so the UI offered stock that B3 forbids issuing — and the inventory
     * dashboard, which aggregates with a lot-status filter, reported {@code 0} for the same rows.
     * On-hand stays truthful: the stock exists, it is just not usable yet.
     */
    @Test
    @DisplayName("list: a lot on HOLD reports zero available but keeps its real on-hand quantity")
    void list_lotOnHold_reportsZeroAvailableWithoutHidingOnHand() {
        StockBalance balance = balance(lot(LotStatus.HOLD), new BigDecimal("2"), BigDecimal.ZERO);
        when(balanceRepository.searchLots(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(balance)));
        when(movementRepository.findFirstByLotLotIdAndMovementTypeOrderByCreatedAtAsc(lotId, MovementType.RECEIVE))
                .thenReturn(Optional.empty());

        InventoryLotResponse response = service.list(
                warehouseId, null, null, null, null, null, PageRequest.of(0, 20)).content().get(0);

        assertThat(response.status()).isEqualTo(LotStatus.HOLD.name());
        assertThat(response.availableQuantity()).isEqualByComparingTo("0");
        assertThat(response.onHandQuantity()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("get: unknown lotId throws RESOURCE_NOT_FOUND")
    void get_unknownLot_throwsResourceNotFound() {
        when(lotRepository.findById(lotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(lotId, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("get: returns real balances[] across warehouses, not a hardcoded single row")
    void get_returnsRealBalancesAcrossWarehouses() {
        InventoryLot lot = lot(LotStatus.AVAILABLE);
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        StockBalance balanceA = balance(lot, new BigDecimal("5"), BigDecimal.ZERO);
        StockBalance balanceB = balance(lot, new BigDecimal("7"), new BigDecimal("2"));
        when(balanceRepository.findByLotLotId(lotId)).thenReturn(List.of(balanceA, balanceB));
        when(movementRepository.findFirstByLotLotIdAndMovementTypeOrderByCreatedAtAsc(lotId, MovementType.RECEIVE))
                .thenReturn(Optional.empty());

        InventoryLotDetailResponse response = service.get(lotId, null);

        assertThat(response.balances()).hasSize(2);
        assertThat(response.sourceReferenceType()).isNull();
    }

    @Test
    @DisplayName("get: warehouse-scoped detail returns only that balance and its source movement")
    void get_withWarehouse_returnsOnlyThatWarehouseData() {
        InventoryLot lot = lot(LotStatus.AVAILABLE);
        StockBalance scopedBalance = balance(lot, new BigDecimal("5"), BigDecimal.ZERO);
        StockMovement scopedOrigin = receiveMovement("PURCHASE_ORDER", "po-1");
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                itemId, warehouseId, lotId)).thenReturn(Optional.of(scopedBalance));
        when(movementRepository
                .findFirstByWarehouseWarehouseIdAndLotLotIdAndMovementTypeOrderByCreatedAtAsc(
                        warehouseId, lotId, MovementType.RECEIVE))
                .thenReturn(Optional.of(scopedOrigin));

        InventoryLotDetailResponse response = service.get(lotId, warehouseId);

        assertThat(response.balances()).singleElement()
                .satisfies(balance -> assertThat(balance.warehouseId()).isEqualTo(warehouseId));
        assertThat(response.sourceReferenceType()).isEqualTo("PURCHASE_ORDER");
        assertThat(response.sourceReferenceId()).isEqualTo("po-1");
        verify(balanceRepository, never()).findByLotLotId(any());
        verify(movementRepository, never())
                .findFirstByLotLotIdAndMovementTypeOrderByCreatedAtAsc(any(), any());
    }

    @Test
    @DisplayName("get: lot without a balance in the requested warehouse returns RESOURCE_NOT_FOUND")
    void get_withWarehouseMissingBalance_throwsResourceNotFound() {
        InventoryLot lot = lot(LotStatus.AVAILABLE);
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                itemId, warehouseId, lotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(lotId, warehouseId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));

        verifyNoInteractions(movementRepository);
    }

    @Test
    @DisplayName("changeStatus: happy path resolves the balance and delegates to changeLotStatus")
    void changeStatus_happyPath_delegatesToChangeLotStatus() {
        InventoryLot lot = lot(LotStatus.AVAILABLE);
        StockBalance balance = balance(lot, new BigDecimal("6"), BigDecimal.ZERO);
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(itemId, warehouseId, lotId))
                .thenReturn(Optional.of(balance));
        when(movementService.changeLotStatus(any(LotStatusChangeCommand.class), eq("KEY-1")))
                .thenReturn(new InventoryMovementResult(receiveMovement("MANUAL", null), true));

        InventoryLotStatusChangeRequest request = new InventoryLotStatusChangeRequest(
                warehouseId, LotStatus.HOLD, "Found damage", null, null);
        service.changeStatus(lotId, request, "KEY-1");

        ArgumentCaptor<LotStatusChangeCommand> captor = ArgumentCaptor.forClass(LotStatusChangeCommand.class);
        verify(movementService).changeLotStatus(captor.capture(), eq("KEY-1"));
        assertThat(captor.getValue().itemId()).isEqualTo(itemId);
        assertThat(captor.getValue().warehouseId()).isEqualTo(warehouseId);
        assertThat(captor.getValue().lotId()).isEqualTo(lotId);
        assertThat(captor.getValue().newStatus()).isEqualTo(LotStatus.HOLD);
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("6");
        assertThat(captor.getValue().reason()).isEqualTo("Found damage");
    }

    @Test
    @DisplayName("changeStatus: escaping HOLD is blocked when the lot still needs QC disposition")
    void changeStatus_holdEscapeBlockedWhenQcRequired() {
        InventoryLot lot = lot(LotStatus.HOLD);
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(lotQcOriginLookupService.requiresQcDispositionBeforeRelease(lotId)).thenReturn(true);

        InventoryLotStatusChangeRequest request = new InventoryLotStatusChangeRequest(
                warehouseId, LotStatus.AVAILABLE, "Trying to skip QC", null, null);

        assertThatThrownBy(() -> service.changeStatus(lotId, request, "KEY-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOT_NOT_ELIGIBLE));

        verifyNoInteractions(movementService, balanceRepository);
    }

    @Test
    @DisplayName("changeStatus: escaping HOLD is allowed once the lookup says QC is not required")
    void changeStatus_holdEscapeAllowedWhenQcNotRequired() {
        InventoryLot lot = lot(LotStatus.HOLD);
        StockBalance balance = balance(lot, new BigDecimal("3"), BigDecimal.ZERO);
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(lotQcOriginLookupService.requiresQcDispositionBeforeRelease(lotId)).thenReturn(false);
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(itemId, warehouseId, lotId))
                .thenReturn(Optional.of(balance));
        when(movementService.changeLotStatus(any(LotStatusChangeCommand.class), eq("KEY-1")))
                .thenReturn(new InventoryMovementResult(receiveMovement("MANUAL", null), true));

        InventoryLotStatusChangeRequest request = new InventoryLotStatusChangeRequest(
                warehouseId, LotStatus.AVAILABLE, "Manually re-released", null, null);

        service.changeStatus(lotId, request, "KEY-1");

        verify(movementService).changeLotStatus(any(LotStatusChangeCommand.class), eq("KEY-1"));
    }

    @Test
    @DisplayName("changeStatus: transitioning into HOLD never consults the QC lookup")
    void changeStatus_intoHold_neverConsultsQcLookup() {
        InventoryLot lot = lot(LotStatus.AVAILABLE);
        StockBalance balance = balance(lot, new BigDecimal("4"), BigDecimal.ZERO);
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(itemId, warehouseId, lotId))
                .thenReturn(Optional.of(balance));
        when(movementService.changeLotStatus(any(LotStatusChangeCommand.class), eq("KEY-1")))
                .thenReturn(new InventoryMovementResult(receiveMovement("MANUAL", null), true));

        InventoryLotStatusChangeRequest request = new InventoryLotStatusChangeRequest(
                warehouseId, LotStatus.HOLD, "Manual quarantine", null, null);

        service.changeStatus(lotId, request, "KEY-1");

        verify(lotQcOriginLookupService, never()).requiresQcDispositionBeforeRelease(any());
    }

    @Test
    @DisplayName("changeStatus: EXPIRED target is rejected before touching anything")
    void changeStatus_expiredTarget_throwsBeforeAnyWrite() {
        InventoryLotStatusChangeRequest request = new InventoryLotStatusChangeRequest(
                warehouseId, LotStatus.EXPIRED, "Trying to expire manually", null, null);

        assertThatThrownBy(() -> service.changeStatus(lotId, request, "KEY-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(lotRepository, movementService, balanceRepository, lotQcOriginLookupService);
    }

    private InventoryLot lot(LotStatus status) {
        return InventoryLot.builder()
                .lotId(lotId)
                .item(item())
                .lotCode("LOT-1")
                .status(status)
                .receivedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();
    }

    private StockBalance balance(InventoryLot lot, BigDecimal quantity, BigDecimal reserved) {
        return StockBalance.builder()
                .item(item())
                .warehouse(warehouse())
                .lot(lot)
                .quantity(quantity)
                .reservedQuantity(reserved)
                .build();
    }

    private StockMovement receiveMovement(String referenceType, String referenceId) {
        return StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(item())
                .warehouse(warehouse())
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(BigDecimal.ONE)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .createdAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
    }

    private Item item() {
        return Item.builder()
                .itemId(itemId)
                .company(company())
                .code("RM-001")
                .name("Steel Coil")
                .type(ItemType.RAW_MATERIAL)
                .unit("KG")
                .lotTracked(true)
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse() {
        Plant plant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(company())
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
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Company company() {
        return Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
