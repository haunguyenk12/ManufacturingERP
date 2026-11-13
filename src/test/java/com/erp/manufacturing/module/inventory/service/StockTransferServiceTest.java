package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.StockTransferLineRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.*;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockTransferService tests (G4-2)")
class StockTransferServiceTest {

    @Mock StockTransferRepository transferRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock ItemRepository itemRepository;
    @Mock InventoryLotRepository lotRepository;
    @Mock StockBalanceRepository balanceRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock UserLookupService userLookupService;

    // R3: idempotency hashing, plant cross-check and mapping are real logic — real instances.
    final IdempotencySupport idempotency = new IdempotencySupport(new ObjectMapper());
    StockTransferService service;
    final List<StockMovement> savedMovements = new java.util.ArrayList<>();

    final Company company = Company.builder().companyId(UUID.randomUUID()).code("ACME").name("ACME")
            .status(OrganizationStatus.ACTIVE).build();
    final Plant plant = plant(OrganizationStatus.ACTIVE);
    final Warehouse from = warehouse(plant, "WH-A");
    final Warehouse to = warehouse(plant, "WH-B");
    final Item item = item(false);
    final Item lotItem = item(true);

    @BeforeEach
    void setUp() {
        service = new StockTransferService(transferRepository, new OrganizationLookupService(mock(CompanyRepository.class), mock(PlantRepository.class), warehouseRepository), itemRepository, lotRepository,
                balanceRepository, movementRepository, idempotency, new TraceIdProvider(),
                new PlantContextResolver(), userLookupService, new InventoryMapper());
    }

    @Test
    @DisplayName("create: moves free stock with an OUT/IN pair per line, all in one document")
    void create_happyPath_writesAPairOfMovementsPerLineAndMovesTheStock() {
        stubWarehouses();
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        StockBalance source = balance(item, from, null, "10", "3");
        when(balanceRepository.lockPositionsOfItems(eq(from.getWarehouseId()), any()))
                .thenReturn(List.of(source));
        StockBalance destination = balance(item, to, null, "1", "0");
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(item.getItemId(), to.getWarehouseId()))
                .thenReturn(Optional.of(destination));
        stubWrites();

        StockTransferResponse response = service.create(request(new StockTransferLineRequest(
                item.getItemId(), null, new BigDecimal("7"))), "TR-KEY-1", null);

        assertThat(source.getQuantity()).isEqualByComparingTo("3");
        assertThat(destination.getQuantity()).isEqualByComparingTo("8");
        verify(movementRepository, times(2)).save(any(StockMovement.class));
        StockMovement out = savedMovements.get(0);
        StockMovement in = savedMovements.get(1);
        assertThat(out.getMovementType()).isEqualTo(MovementType.TRANSFER_OUT);
        assertThat(out.getDirection()).isEqualTo(MovementDirection.OUT);
        assertThat(out.getWarehouse()).isSameAs(from);
        assertThat(in.getMovementType()).isEqualTo(MovementType.TRANSFER_IN);
        assertThat(in.getDirection()).isEqualTo(MovementDirection.IN);
        assertThat(in.getWarehouse()).isSameAs(to);
        assertThat(out.getIdempotencyKey()).isEqualTo("TR-KEY-1:L1").isEqualTo(in.getIdempotencyKey());
        assertThat(out.getReferenceType()).isEqualTo("STOCK_TRANSFER");
        assertThat(out.getReferenceId()).isEqualTo(response.transferId().toString());
        assertThat(response.status()).isEqualTo("POSTED");
        assertThat(response.postedAt().getNano() % 1_000).as("postedAt is stored to the microsecond").isZero();
        assertThat(response.lines()).singleElement().satisfies(line -> {
            assertThat(line.lineNo()).isEqualTo(1);
            assertThat(line.quantity()).isEqualByComparingTo("7");
            assertThat(line.outMovementId()).isEqualTo(out.getMovementId());
            assertThat(line.inMovementId()).isEqualTo(in.getMovementId());
        });
    }

    @Test
    @DisplayName("create: the same warehouse on both sides is VALIDATION_ERROR on toWarehouseId, nothing read or written")
    void create_sameWarehouse_isRejectedBeforeAnyLookup() {
        StockTransferRequest request = new StockTransferRequest(from.getWarehouseId(), from.getWarehouseId(), "Move",
                List.of(new StockTransferLineRequest(item.getItemId(), null, BigDecimal.ONE)));

        assertThatThrownBy(() -> service.create(request, "TR-KEY-2", null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("toWarehouseId");
                });

        verifyNoInteractions(warehouseRepository, balanceRepository, movementRepository);
        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: warehouses of two plants are RESOURCE_SCOPE_MISMATCH")
    void create_warehousesOfDifferentPlants_isRejected() {
        Warehouse elsewhere = warehouse(plant(OrganizationStatus.ACTIVE), "WH-X");
        when(warehouseRepository.findById(from.getWarehouseId())).thenReturn(Optional.of(from));
        when(warehouseRepository.findById(elsewhere.getWarehouseId())).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.create(new StockTransferRequest(from.getWarehouseId(),
                elsewhere.getWarehouseId(), "Move",
                List.of(new StockTransferLineRequest(item.getItemId(), null, BigDecimal.ONE))), "TR-KEY-3", null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verifyNoInteractions(balanceRepository, movementRepository);
    }

    @Test
    @DisplayName("create: an inactive plant is RESOURCE_INACTIVE even when both warehouses are active")
    void create_inactivePlant_isRejected() {
        Plant closed = plant(OrganizationStatus.INACTIVE);
        Warehouse a = warehouse(closed, "WH-1");
        Warehouse b = warehouse(closed, "WH-2");
        when(warehouseRepository.findById(a.getWarehouseId())).thenReturn(Optional.of(a));
        when(warehouseRepository.findById(b.getWarehouseId())).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.create(new StockTransferRequest(a.getWarehouseId(), b.getWarehouseId(),
                "Move", List.of(new StockTransferLineRequest(item.getItemId(), null, BigDecimal.ONE))), "TR-KEY-4", null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        verifyNoInteractions(balanceRepository, movementRepository);
    }

    @Test
    @DisplayName("create: X-Plant-Id naming another plant is STATE_CONFLICT before any line is read")
    void create_plantHeaderMismatch_isStateConflict() {
        stubWarehouses();

        assertThatThrownBy(() -> service.create(request(new StockTransferLineRequest(
                item.getItemId(), null, BigDecimal.ONE)), "TR-KEY-5", UUID.randomUUID().toString()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(itemRepository, balanceRepository, movementRepository);
    }

    @Test
    @DisplayName("create: two lines on the same item and lot are VALIDATION_ERROR pointing at the second line (D8)")
    void create_duplicatePosition_isRejectedOnTheDuplicateLine() {
        stubWarehouses();

        assertThatThrownBy(() -> service.create(request(
                new StockTransferLineRequest(item.getItemId(), null, new BigDecimal("6")),
                new StockTransferLineRequest(item.getItemId(), null, new BigDecimal("5"))), "TR-KEY-6", null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[1]");
                });

        verifyNoInteractions(itemRepository, balanceRepository, movementRepository);
    }

    @Test
    @DisplayName("create: a lot on HOLD is LOT_NOT_ELIGIBLE")
    void create_lotOnHold_isRejected() {
        stubWarehouses();
        when(itemRepository.findById(lotItem.getItemId())).thenReturn(Optional.of(lotItem));
        InventoryLot lot = lot(LotStatus.HOLD, null);
        when(lotRepository.findById(lot.getLotId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> service.create(request(new StockTransferLineRequest(
                lotItem.getItemId(), lot.getLotId(), BigDecimal.ONE)), "TR-KEY-7", null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOT_NOT_ELIGIBLE));

        verifyNoInteractions(balanceRepository, movementRepository);
    }

    @Test
    @DisplayName("create: an AVAILABLE lot past its expiry is LOT_NOT_ELIGIBLE")
    void create_expiredLot_isRejected() {
        stubWarehouses();
        when(itemRepository.findById(lotItem.getItemId())).thenReturn(Optional.of(lotItem));
        InventoryLot lot = lot(LotStatus.AVAILABLE, Instant.now().minusSeconds(5));
        when(lotRepository.findById(lot.getLotId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> service.create(request(new StockTransferLineRequest(
                lotItem.getItemId(), lot.getLotId(), BigDecimal.ONE)), "TR-KEY-8", null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOT_NOT_ELIGIBLE));

        verifyNoInteractions(balanceRepository, movementRepository);
    }

    @Test
    @DisplayName("create: a lot-tracked line without lotId is LOT_REQUIRED on lines[i].lotId")
    void create_lotTrackedLineWithoutLot_isLotRequired() {
        stubWarehouses();
        when(itemRepository.findById(lotItem.getItemId())).thenReturn(Optional.of(lotItem));

        assertThatThrownBy(() -> service.create(request(new StockTransferLineRequest(
                lotItem.getItemId(), null, BigDecimal.ONE)), "TR-KEY-9", null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.LOT_REQUIRED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[0].lotId");
                });
    }

    @Test
    @DisplayName("create: one line over free stock fails the whole document, naming that line, before any write")
    void create_oneLineOverFreeStock_failsTheWholeTransferBeforeAnyWrite() {
        stubWarehouses();
        Item second = item(false);
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(itemRepository.findById(second.getItemId())).thenReturn(Optional.of(second));
        StockBalance first = balance(item, from, null, "10", "0");
        StockBalance reservedAway = balance(second, from, null, "10", "8");
        when(balanceRepository.lockPositionsOfItems(eq(from.getWarehouseId()), any()))
                .thenReturn(List.of(first, reservedAway));

        assertThatThrownBy(() -> service.create(request(
                new StockTransferLineRequest(item.getItemId(), null, new BigDecimal("5")),
                new StockTransferLineRequest(second.getItemId(), null, new BigDecimal("3"))), "TR-KEY-10", null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[1].quantity");
                });

        assertThat(first.getQuantity()).isEqualByComparingTo("10");
        verify(transferRepository, never()).save(any());
        verifyNoInteractions(movementRepository);
    }

    @Test
    @DisplayName("create: replaying the same key and payload returns the original transfer and moves nothing (R9)")
    void create_replaySamePayload_returnsTheOriginalWithoutMovingStock() {
        StockTransferRequest request = request(new StockTransferLineRequest(item.getItemId(), null, BigDecimal.ONE));
        StockTransfer original = postedTransfer(idempotency.payloadHash(request));
        when(transferRepository.findByIdempotencyKey("TR-KEY-11")).thenReturn(Optional.of(original));
        when(transferRepository.findDetailByTransferId(original.getTransferId())).thenReturn(Optional.of(original));

        StockTransferResponse response = service.create(request, "TR-KEY-11", null);

        assertThat(response.transferId()).isEqualTo(original.getTransferId());
        verifyNoInteractions(warehouseRepository, itemRepository, balanceRepository, movementRepository);
        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: replaying the key with another payload is IDEMPOTENCY_CONFLICT")
    void create_replayDifferentPayload_isIdempotencyConflict() {
        StockTransfer original = postedTransfer(idempotency.payloadHash(
                request(new StockTransferLineRequest(item.getItemId(), null, BigDecimal.ONE))));
        when(transferRepository.findByIdempotencyKey("TR-KEY-12")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.create(request(new StockTransferLineRequest(
                item.getItemId(), null, BigDecimal.TEN)), "TR-KEY-12", null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.IDEMPOTENCY_CONFLICT));

        verifyNoInteractions(balanceRepository, movementRepository);
    }

    @Test
    @DisplayName("list: a warehouse of another plant is RESOURCE_SCOPE_MISMATCH, not an empty page")
    void list_warehouseOfAnotherPlant_isRejected() {
        when(warehouseRepository.findById(from.getWarehouseId())).thenReturn(Optional.of(from));

        assertThatThrownBy(() -> service.list(UUID.randomUUID(), from.getWarehouseId(), null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verifyNoInteractions(transferRepository);
    }

    @Test
    @DisplayName("list: usernames are resolved for the whole page in one batch (C14)")
    void list_resolvesCreatorsInOneBatch() {
        StockTransfer a = postedTransfer(null);
        StockTransfer b = postedTransfer(null);
        UUID actor = UUID.randomUUID();
        a.setCreatedBy(actor);
        b.setCreatedBy(actor);
        when(transferRepository.search(eq(plant.getPlantId()), isNull(), eq("TR-"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(a, b)));
        when(userLookupService.findUsernames(any())).thenReturn(Map.of(actor, "kho.a"));

        var page = service.list(plant.getPlantId(), null, "  TR- ", null,
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.content()).extracting(StockTransferResponse::createdByUsername).containsExactly("kho.a", "kho.a");
        assertThat(page.content()).allSatisfy(row -> assertThat(row.lines()).isNull());
        verify(userLookupService, times(1)).findUsernames(any());
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    private StockTransferRequest request(StockTransferLineRequest... lines) {
        return new StockTransferRequest(from.getWarehouseId(), to.getWarehouseId(), "Move to line side", List.of(lines));
    }

    private void stubWarehouses() {
        when(warehouseRepository.findById(from.getWarehouseId())).thenReturn(Optional.of(from));
        when(warehouseRepository.findById(to.getWarehouseId())).thenReturn(Optional.of(to));
    }

    private void stubWrites() {
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(invocation -> {
            StockTransfer transfer = invocation.getArgument(0);
            transfer.setTransferId(UUID.randomUUID());
            transfer.assignCode();
            return transfer;
        });
        when(transferRepository.saveAndFlush(any(StockTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceRepository.save(any(StockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            StockMovement saved = StockMovement.builder()
                    .movementId(UUID.randomUUID())
                    .item(movement.getItem()).warehouse(movement.getWarehouse()).lot(movement.getLot())
                    .movementType(movement.getMovementType()).direction(movement.getDirection())
                    .quantity(movement.getQuantity()).reason(movement.getReason())
                    .referenceType(movement.getReferenceType()).referenceId(movement.getReferenceId())
                    .idempotencyKey(movement.getIdempotencyKey()).createdAt(movement.getCreatedAt())
                    .build();
            savedMovements.add(saved);
            return saved;
        });
    }

    private StockTransfer postedTransfer(String payloadHash) {
        StockTransfer transfer = StockTransfer.builder()
                .transferId(UUID.randomUUID())
                .code("TR-ABCD1234")
                .plant(plant).fromWarehouse(from).toWarehouse(to)
                .reason("Move").postedAt(Instant.parse("2026-10-15T00:00:00Z"))
                .idempotencyKey("KEY").payloadHash(payloadHash)
                .build();
        return transfer;
    }

    private Plant plant(OrganizationStatus status) {
        return Plant.builder().plantId(UUID.randomUUID()).company(company).code("P1").name("Plant 1")
                .status(status).build();
    }

    private Warehouse warehouse(Plant owner, String code) {
        return Warehouse.builder().warehouseId(UUID.randomUUID()).plant(owner).code(code).name(code)
                .type(WarehouseType.RAW_MATERIAL).status(OrganizationStatus.ACTIVE).build();
    }

    private Item item(boolean lotTracked) {
        return Item.builder().itemId(UUID.randomUUID()).company(company).code("RM-" + lotTracked)
                .name("Steel").type(ItemType.RAW_MATERIAL).unit("KG").lotTracked(lotTracked)
                .status(ItemStatus.ACTIVE).build();
    }

    private InventoryLot lot(LotStatus status, Instant expiresAt) {
        return InventoryLot.builder().lotId(UUID.randomUUID()).item(lotItem).lotCode("LOT-1")
                .status(status).expiresAt(expiresAt).build();
    }

    private StockBalance balance(Item owner, Warehouse warehouse, InventoryLot lot, String quantity, String reserved) {
        return StockBalance.builder().item(owner).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal(quantity)).reservedQuantity(new BigDecimal(reserved)).build();
    }
}
