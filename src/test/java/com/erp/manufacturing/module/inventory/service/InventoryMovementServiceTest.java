package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryMovementService tests")
class InventoryMovementServiceTest {

    @Mock ItemRepository itemRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock InventoryLotRepository lotRepository;
    @Mock StockBalanceRepository balanceRepository;
    @Mock StockMovementRepository movementRepository;

    InventoryMovementService service;

    // R3: IdempotencySupport carries real hashing logic, so it is a real instance, not a mock.
    IdempotencySupport idempotency = new IdempotencySupport(new ObjectMapper());

    @BeforeEach
    void setUp() {
        service = new InventoryMovementService(
                itemRepository,
                warehouseRepository,
                lotRepository,
                balanceRepository,
                movementRepository,
                idempotency,
                new TraceIdProvider());
    }

    @Test
    void receive_lotTrackedItem_createsLotAndIncreasesBalance() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        Item item = item(itemId, companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-1", MovementType.RECEIVE)).thenReturn(Optional.empty());
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
    void receive_newLot_withHoldStatus_shouldCreateLotInHold() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        Item item = item(itemId, companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-HOLD", MovementType.RECEIVE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(lotRepository.findByItemItemIdAndLotCode(itemId, "LOT-WO-1")).thenReturn(Optional.empty());
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
                itemId, warehouseId, null, "LOT-WO-1", new BigDecimal("5"), null, "WORK_ORDER", "WO-1"),
                "KEY-HOLD", LotStatus.HOLD);

        ArgumentCaptor<InventoryLot> lotCaptor = ArgumentCaptor.forClass(InventoryLot.class);
        verify(lotRepository).save(lotCaptor.capture());
        assertThat(lotCaptor.getValue().getStatus()).isEqualTo(LotStatus.HOLD);
    }

    @Test
    void receive_existingLot_shouldKeepCurrentLotStatus() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        Item item = item(itemId, companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        InventoryLot existingLot = InventoryLot.builder()
                .lotId(lotId)
                .item(item)
                .lotCode("LOT-EXISTING")
                .status(LotStatus.AVAILABLE)
                .build();

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-EXISTING", MovementType.RECEIVE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(lotRepository.findByItemItemIdAndLotCode(itemId, "LOT-EXISTING")).thenReturn(Optional.of(existingLot));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(itemId, warehouseId, lotId))
                .thenReturn(Optional.empty());
        when(balanceRepository.save(any(StockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, "LOT-EXISTING", BigDecimal.ONE, null, "WORK_ORDER", "WO-1"),
                "KEY-EXISTING", LotStatus.HOLD);

        assertThat(existingLot.getStatus()).isEqualTo(LotStatus.AVAILABLE);
        verify(lotRepository, never()).save(any());
    }

    @Test
    void receive_defaultOverload_shouldStillCreateAvailableLot() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        Item item = item(itemId, companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-DEFAULT", MovementType.RECEIVE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(lotRepository.findByItemItemIdAndLotCode(itemId, "LOT-DEFAULT")).thenReturn(Optional.empty());
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
                itemId, warehouseId, null, "LOT-DEFAULT", BigDecimal.ONE, null, "PO", "PO-1"), "KEY-DEFAULT");

        ArgumentCaptor<InventoryLot> lotCaptor = ArgumentCaptor.forClass(InventoryLot.class);
        verify(lotRepository).save(lotCaptor.capture());
        assertThat(lotCaptor.getValue().getStatus()).isEqualTo(LotStatus.AVAILABLE);
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
        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-1", MovementType.RECEIVE)).thenReturn(Optional.of(existing));

        service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null), "KEY-1");

        verifyNoInteractions(itemRepository, warehouseRepository, lotRepository, balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void receive_sameIdempotencyKeyDifferentPayload_throwsIdempotencyConflictAndDoesNotTouchStock() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        InventoryReceiveCommand original = new InventoryReceiveCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null);
        StockMovement existing = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("KEY-1")
                .payloadHash(idempotency.payloadHash(original))
                .createdAt(Instant.now())
                .build();
        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-1", MovementType.RECEIVE)).thenReturn(Optional.of(existing));

        // Same key, but quantity 1 -> 99: replaying this silently used to return the original
        // movement and discard the new payload.
        InventoryReceiveCommand tampered = new InventoryReceiveCommand(
                itemId, warehouseId, null, null, new BigDecimal("99"), null, null, null);

        assertThatThrownBy(() -> service.receive(tampered, "KEY-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.IDEMPOTENCY_CONFLICT));

        verifyNoInteractions(itemRepository, warehouseRepository, lotRepository, balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void receive_legacyMovementWithoutPayloadHash_stillReplaysInsteadOfConflicting() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        // Rows written before V25 have no fingerprint; they must keep replaying, not start failing.
        StockMovement legacy = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("LEGACY-1")
                .createdAt(Instant.now())
                .build();
        when(movementRepository.findByIdempotencyKeyAndMovementType("LEGACY-1", MovementType.RECEIVE)).thenReturn(Optional.of(legacy));

        InventoryMovementResult result = service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, null, new BigDecimal("99"), null, null, null), "LEGACY-1");

        assertThat(result.created()).isFalse();
        assertThat(result.movement()).isSameAs(legacy);
        verify(movementRepository, never()).save(any());
    }

    /**
     * D6 / debt #10 — the point of the phase. Before V37 one {@code Idempotency-Key} was unique across
     * the whole ledger and the replay lookup ignored the operation, so this second call returned the
     * RECEIVE document and issued nothing while answering 200.
     * <p>
     * The stub answers from a {@code (key, type)} table rather than a fixed value, so pointing the
     * issue path at the wrong {@link MovementType} turns this test red — the mutation seam.
     */
    @Test
    void issue_sameKeyAlreadyUsedByAReceive_createsAnIndependentIssueMovement() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        StockBalance balance = StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .quantity(new BigDecimal("10"))
                .build();

        StockMovement earlierReceive = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(new BigDecimal("10"))
                .idempotencyKey("SHARED-KEY")
                .createdAt(Instant.now())
                .build();
        Map<MovementType, StockMovement> ledger = Map.of(MovementType.RECEIVE, earlierReceive);
        when(movementRepository.findByIdempotencyKeyAndMovementType(eq("SHARED-KEY"), any(MovementType.class)))
                .thenAnswer(invocation -> Optional.ofNullable(ledger.get(invocation.getArgument(1))));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.of(balance));
        when(balanceRepository.save(balance)).thenReturn(balance);
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementResult result = service.issue(new InventoryIssueCommand(
                itemId, warehouseId, null, null, new BigDecimal("4"), null, null, null), "SHARED-KEY");

        assertThat(result.created()).isTrue();
        assertThat(result.movement()).isNotSameAs(earlierReceive);
        assertThat(result.movement().getMovementType()).isEqualTo(MovementType.ISSUE);
        assertThat(balance.getQuantity()).isEqualByComparingTo("6");
        // C15: exactly one lookup, and with the ISSUE scope spelled out — not any().
        verify(movementRepository).findByIdempotencyKeyAndMovementType("SHARED-KEY", MovementType.ISSUE);
        verify(movementRepository, never())
                .findByIdempotencyKeyAndMovementType("SHARED-KEY", MovementType.RECEIVE);
    }

    /**
     * Same-scope replay still wins: {@code issueReserved} writes {@link MovementType#ISSUE} exactly like
     * {@code issue}, deliberately sharing one replay scope (both are "goods out"), so a key already used
     * by an issue must not post stock a second time through the other entry point.
     */
    @Test
    void issueReserved_replaysMovementCreatedByPlainIssue_becauseBothAreIssueScope() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        StockMovement existing = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.ISSUE)
                .direction(MovementDirection.OUT)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("ISSUE-KEY")
                .createdAt(Instant.now())
                .build();
        when(movementRepository.findByIdempotencyKeyAndMovementType("ISSUE-KEY", MovementType.ISSUE))
                .thenReturn(Optional.of(existing));

        InventoryMovementResult result = service.issueReserved(new InventoryIssueCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null), "ISSUE-KEY");

        assertThat(result.created()).isFalse();
        assertThat(result.movement()).isSameAs(existing);
        verifyNoInteractions(itemRepository, warehouseRepository, lotRepository, balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    /**
     * {@code adjust} picks its type from the sign of the delta, so an inbound and an outbound adjustment
     * are two operations sharing one key space. This is the case option (b) of the plan (reading both
     * types at once) could not have covered.
     */
    @Test
    void adjust_sameKeyAlreadyUsedInbound_createsAnIndependentOutboundMovement() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        StockBalance balance = StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .quantity(new BigDecimal("8"))
                .build();

        StockMovement earlierAdjustIn = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.ADJUST_IN)
                .direction(MovementDirection.IN)
                .quantity(new BigDecimal("8"))
                .idempotencyKey("ADJ-KEY")
                .createdAt(Instant.now())
                .build();
        Map<MovementType, StockMovement> ledger = Map.of(MovementType.ADJUST_IN, earlierAdjustIn);
        when(movementRepository.findByIdempotencyKeyAndMovementType(eq("ADJ-KEY"), any(MovementType.class)))
                .thenAnswer(invocation -> Optional.ofNullable(ledger.get(invocation.getArgument(1))));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.of(balance));
        when(balanceRepository.save(balance)).thenReturn(balance);
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementResult result = service.adjust(new InventoryAdjustCommand(
                itemId, warehouseId, null, null, new BigDecimal("-3"), "Cycle count", null, null), "ADJ-KEY");

        assertThat(result.created()).isTrue();
        assertThat(result.movement().getMovementType()).isEqualTo(MovementType.ADJUST_OUT);
        assertThat(balance.getQuantity()).isEqualByComparingTo("5");
        verify(movementRepository).findByIdempotencyKeyAndMovementType("ADJ-KEY", MovementType.ADJUST_OUT);
    }

    /**
     * D6 breaking change: {@code adjust} must know the sign before it can scope the replay lookup, so a
     * zero delta is now rejected <em>before</em> the item/warehouse lookups run — hence
     * {@code verifyNoInteractions} on every repository, including the movement one.
     */
    @Test
    void adjust_zeroDelta_failsBeforeAnyLookupIncludingTheReplayLookup() {
        assertThatThrownBy(() -> service.adjust(new InventoryAdjustCommand(
                UUID.randomUUID(), UUID.randomUUID(), null, null, BigDecimal.ZERO, null, null, null), "ADJ-ZERO"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.NEGATIVE_QUANTITY));

        verifyNoInteractions(movementRepository, itemRepository, warehouseRepository, lotRepository, balanceRepository);
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

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-2", MovementType.ISSUE)).thenReturn(Optional.empty());
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

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-RESERVED", MovementType.ISSUE)).thenReturn(Optional.empty());
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

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-RES-ISSUE", MovementType.ISSUE)).thenReturn(Optional.empty());
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

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-3", MovementType.ISSUE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> service.issue(new InventoryIssueCommand(
                itemId, warehouseId, lotId, null, BigDecimal.ONE, null, null, null), "KEY-3"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOT_NOT_ELIGIBLE));

        verifyNoInteractions(balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void receive_itemAndWarehouseInDifferentCompanies_fails() {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, UUID.randomUUID(), false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, UUID.randomUUID(), OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-4", MovementType.RECEIVE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        // Master-data validation, not a lot status problem — deliberately left at 422 when F5
        // moved the lot-status refusals to LOT_NOT_ELIGIBLE (409).
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
