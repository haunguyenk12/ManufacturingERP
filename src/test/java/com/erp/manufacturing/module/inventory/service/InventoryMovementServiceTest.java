package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.SerialNumberRepository;
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
    @Mock SerialNumberRepository serialNumberRepository;
    @Mock StockBalanceRepository balanceRepository;
    @Mock StockMovementRepository movementRepository;

    InventoryMovementService service;

    // R3: IdempotencySupport carries real hashing logic, so it is a real instance, not a mock.
    // JavaTimeModule: the production mapper (Boot) has it, and InventoryReceiveCommand.expiresAt is an Instant.
    IdempotencySupport idempotency = new IdempotencySupport(
            new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));

    @BeforeEach
    void setUp() {
        service = new InventoryMovementService(
                itemRepository,
                warehouseRepository,
                lotRepository,
                serialNumberRepository,
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
                itemId, warehouseId, null, "LOT-1", new BigDecimal("10.500"), "PO receipt", "PO", "PO-1", null, null, null), "KEY-1");

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
                itemId, warehouseId, null, "LOT-WO-1", new BigDecimal("5"), null, "WORK_ORDER", "WO-1", null, null, null),
                "KEY-HOLD", LotStatus.HOLD);

        ArgumentCaptor<InventoryLot> lotCaptor = ArgumentCaptor.forClass(InventoryLot.class);
        verify(lotRepository).save(lotCaptor.capture());
        assertThat(lotCaptor.getValue().getStatus()).isEqualTo(LotStatus.HOLD);
    }

    @Test
    void receive_nonTrackedItemWithHoldStatus_increasesOnHandAndQualityHoldButNotAvailable() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-NONTRACKED-HOLD", MovementType.RECEIVE))
                .thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.empty());
        when(balanceRepository.save(any(StockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.receive(new InventoryReceiveCommand(
                        itemId, warehouseId, null, null, new BigDecimal("5"), null,
                        "WORK_ORDER", "WO-1", null, null, null),
                "KEY-NONTRACKED-HOLD", LotStatus.HOLD);

        ArgumentCaptor<StockBalance> balanceCaptor = ArgumentCaptor.forClass(StockBalance.class);
        verify(balanceRepository).save(balanceCaptor.capture());
        StockBalance balance = balanceCaptor.getValue();
        assertThat(balance.getQuantity()).isEqualByComparingTo("5");
        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("0");
        assertThat(balance.getQualityHoldQuantity()).isEqualByComparingTo("5");
        assertThat(balance.availableQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void releaseQualityHold_nonTrackedStock_makesItAvailableWithoutChangingOnHand() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        StockBalance balance = StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .quantity(new BigDecimal("8"))
                .qualityHoldQuantity(new BigDecimal("5"))
                .build();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.of(balance));

        service.releaseQualityHold(itemId, warehouseId, new BigDecimal("5"));

        assertThat(balance.getQuantity()).isEqualByComparingTo("8");
        assertThat(balance.getQualityHoldQuantity()).isEqualByComparingTo("0");
        assertThat(balance.availableQuantity()).isEqualByComparingTo("8");
        verify(balanceRepository).save(balance);
        verifyNoInteractions(movementRepository);
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
                itemId, warehouseId, null, "LOT-EXISTING", BigDecimal.ONE, null, "WORK_ORDER", "WO-1", null, null, null),
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
                itemId, warehouseId, null, "LOT-DEFAULT", BigDecimal.ONE, null, "PO", "PO-1", null, null, null), "KEY-DEFAULT");

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
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, null, null, null), "KEY-1");

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
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, null, null, null);
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
                itemId, warehouseId, null, null, new BigDecimal("99"), null, null, null, null, null, null);

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
                itemId, warehouseId, null, null, new BigDecimal("99"), null, null, null, null, null, null), "LEGACY-1");

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
                itemId, warehouseId, null, null, new BigDecimal("4"), null, null, null, null, null), "SHARED-KEY");

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
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, null, null), "ISSUE-KEY");

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
                itemId, warehouseId, null, null, new BigDecimal("-3"), "Cycle count", null, null, null, null), "ADJ-KEY");

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
                UUID.randomUUID(), UUID.randomUUID(), null, null, BigDecimal.ZERO, null, null, null, null, null), "ADJ-ZERO"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.NEGATIVE_QUANTITY));

        verifyNoInteractions(movementRepository, itemRepository, warehouseRepository, lotRepository, balanceRepository);
    }

    /**
     * CF-1 (B263): the lot guard sees the resolved lot and, when it refuses, nothing is written — no
     * balance change, no ledger row (R5).
     */
    @Test
    @SuppressWarnings("unchecked")
    void adjust_lotGuardRefusesTheResolvedLot_noBalanceOrMovementIsWritten() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        Item item = item(itemId, companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        InventoryLot lot = InventoryLot.builder().lotId(lotId).item(item).lotCode("LOT-QC").status(LotStatus.HOLD).build();
        java.util.function.Consumer<InventoryLot> lotGuard = mock(java.util.function.Consumer.class);
        doThrow(com.erp.manufacturing.common.exception.ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, "QC owns it"))
                .when(lotGuard).accept(lot);

        when(movementRepository.findByIdempotencyKeyAndMovementType("ADJ-QC", MovementType.ADJUST_OUT)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> service.adjust(new InventoryAdjustCommand(
                itemId, warehouseId, lotId, null, new BigDecimal("-1"), "Correction", null, null, null, null), "ADJ-QC", lotGuard))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verify(lotGuard).accept(lot);
        verifyNoInteractions(balanceRepository, serialNumberRepository);
        verify(movementRepository, never()).save(any());
    }

    /**
     * CF-1: a replay returns the original movement even if QC now owns the lot — the guard runs only on
     * the first call, after the replay lookup.
     */
    @Test
    @SuppressWarnings("unchecked")
    void adjust_replayOfAnExistingKey_returnsTheOriginalMovementWithoutConsultingTheLotGuard() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        Item item = item(itemId, companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        InventoryAdjustCommand command = new InventoryAdjustCommand(
                itemId, warehouseId, lotId, null, new BigDecimal("-1"), "Correction", null, null, null, null);
        StockMovement existing = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.ADJUST_OUT)
                .direction(MovementDirection.OUT)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("ADJ-REPLAY")
                .payloadHash(idempotency.payloadHash(command))
                .createdAt(Instant.now())
                .build();
        when(movementRepository.findByIdempotencyKeyAndMovementType("ADJ-REPLAY", MovementType.ADJUST_OUT))
                .thenReturn(Optional.of(existing));
        java.util.function.Consumer<InventoryLot> lotGuard = mock(java.util.function.Consumer.class);

        InventoryMovementResult result = service.adjust(command, "ADJ-REPLAY", lotGuard);

        assertThat(result.created()).isFalse();
        assertThat(result.movement()).isSameAs(existing);
        verifyNoInteractions(lotGuard, itemRepository, warehouseRepository, lotRepository, balanceRepository);
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

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-2", MovementType.ISSUE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.issue(new InventoryIssueCommand(
                itemId, warehouseId, null, null, new BigDecimal("5"), null, null, null, null, null), "KEY-2"))
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
                itemId, warehouseId, null, null, new BigDecimal("3"), null, null, null, null, null), "KEY-RESERVED"))
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
                itemId, warehouseId, null, null, new BigDecimal("5"), null, null, null, null, null), "KEY-RES-ISSUE");

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
                itemId, warehouseId, lotId, null, BigDecimal.ONE, null, null, null, null, null), "KEY-3"))
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
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, null, null, null), "KEY-4"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verifyNoInteractions(balanceRepository);
    }

    // ── Serial tracking (P5) ─────────────────────────────────────────────────

    @Test
    void receive_serialTrackedItem_createsSerialAndIncreasesBalance() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID serialId = UUID.randomUUID();
        Item item = serialItem(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-SN-1", MovementType.RECEIVE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(serialNumberRepository.findByItemItemIdAndSerialCode(itemId, "SN-1")).thenReturn(Optional.empty());
        when(serialNumberRepository.save(any(SerialNumber.class))).thenAnswer(invocation -> {
            SerialNumber serial = invocation.getArgument(0);
            serial.setSerialId(serialId);
            return serial;
        });
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.empty());
        when(balanceRepository.save(any(StockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementResult result = service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, null, "SN-1", null), "KEY-SN-1");

        ArgumentCaptor<SerialNumber> serialCaptor = ArgumentCaptor.forClass(SerialNumber.class);
        verify(serialNumberRepository).save(serialCaptor.capture());
        assertThat(serialCaptor.getValue().getSerialCode()).isEqualTo("SN-1");
        assertThat(serialCaptor.getValue().getStatus()).isEqualTo(SerialStatus.AVAILABLE);
        assertThat(result.movement().getSerial().getSerialId()).isEqualTo(serialId);

        ArgumentCaptor<StockBalance> balanceCaptor = ArgumentCaptor.forClass(StockBalance.class);
        verify(balanceRepository).save(balanceCaptor.capture());
        assertThat(balanceCaptor.getValue().getQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void receive_serialTrackedItem_quantityOtherThanOne_throwsBeforeAnyWrite() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = serialItem(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-SN-2", MovementType.RECEIVE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, null, new BigDecimal("2"), null, null, null, null, "SN-2", null), "KEY-SN-2"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(serialNumberRepository, balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void receive_serialTrackedItem_withoutSerialCode_throwsBeforeAnyWrite() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = serialItem(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-SN-3", MovementType.RECEIVE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, null, null, null), "KEY-SN-3"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void receive_serialTrackedItem_duplicateSerialCode_throwsResourceAlreadyExists() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = serialItem(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        SerialNumber existingSerial = SerialNumber.builder()
                .serialId(UUID.randomUUID())
                .item(item)
                .serialCode("SN-4")
                .status(SerialStatus.AVAILABLE)
                .build();

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-SN-4", MovementType.RECEIVE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(serialNumberRepository.findByItemItemIdAndSerialCode(itemId, "SN-4")).thenReturn(Optional.of(existingSerial));

        assertThatThrownBy(() -> service.receive(new InventoryReceiveCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, null, "SN-4", null), "KEY-SN-4"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(serialNumberRepository, never()).save(any());
        verifyNoInteractions(balanceRepository);
    }

    @Test
    void issue_serialTrackedItem_flipsSerialToIssuedAndDecreasesBalance() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID serialId = UUID.randomUUID();
        Item item = serialItem(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        SerialNumber serial = SerialNumber.builder()
                .serialId(serialId)
                .item(item)
                .serialCode("SN-5")
                .status(SerialStatus.AVAILABLE)
                .build();
        StockBalance balance = StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .quantity(BigDecimal.ONE)
                .build();

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-SN-5", MovementType.ISSUE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(serialNumberRepository.findById(serialId)).thenReturn(Optional.of(serial));
        when(serialNumberRepository.save(any(SerialNumber.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId))
                .thenReturn(Optional.of(balance));
        when(balanceRepository.save(balance)).thenReturn(balance);
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementResult result = service.issue(new InventoryIssueCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, serialId, null), "KEY-SN-5");

        assertThat(serial.getStatus()).isEqualTo(SerialStatus.ISSUED);
        assertThat(result.movement().getSerial().getSerialId()).isEqualTo(serialId);
        assertThat(balance.getQuantity()).isEqualByComparingTo("0");
        verify(serialNumberRepository).save(serial);
    }

    @Test
    void issue_serialTrackedItemWithoutSerialId_throwsBeforeStockMutation() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = serialItem(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-SN-5B", MovementType.ISSUE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        // This is exactly the path MaterialIssueService.postFlat hits for a serial-tracked
        // component: it never has a serialId to offer, so it fails here instead of needing a
        // special case of its own.
        assertThatThrownBy(() -> service.issue(new InventoryIssueCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, null, null), "KEY-SN-5B"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(balanceRepository, serialNumberRepository);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void issue_serialNotAvailable_failsBeforeStockMutation() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID serialId = UUID.randomUUID();
        Item item = serialItem(itemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        SerialNumber serial = SerialNumber.builder()
                .serialId(serialId)
                .item(item)
                .serialCode("SN-6")
                .status(SerialStatus.ISSUED)
                .build();

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-SN-6", MovementType.ISSUE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(serialNumberRepository.findById(serialId)).thenReturn(Optional.of(serial));

        assertThatThrownBy(() -> service.issue(new InventoryIssueCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, serialId, null), "KEY-SN-6"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.SERIAL_NOT_ELIGIBLE));

        verifyNoInteractions(balanceRepository);
        verify(serialNumberRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void issue_serialBelongingToDifferentItem_throws() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID otherItemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID serialId = UUID.randomUUID();
        Item item = serialItem(itemId, companyId, ItemStatus.ACTIVE);
        Item otherItem = serialItem(otherItemId, companyId, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);
        SerialNumber serial = SerialNumber.builder()
                .serialId(serialId)
                .item(otherItem)
                .serialCode("SN-7")
                .status(SerialStatus.AVAILABLE)
                .build();

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-SN-7", MovementType.ISSUE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(serialNumberRepository.findById(serialId)).thenReturn(Optional.of(serial));

        assertThatThrownBy(() -> service.issue(new InventoryIssueCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, serialId, null), "KEY-SN-7"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verifyNoInteractions(balanceRepository);
    }

    @Test
    void issue_serialProvidedForNonSerialTrackedItem_throws() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item item = item(itemId, companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(warehouseId, companyId, OrganizationStatus.ACTIVE);

        when(movementRepository.findByIdempotencyKeyAndMovementType("KEY-SN-8", MovementType.ISSUE)).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.issue(new InventoryIssueCommand(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null, UUID.randomUUID(), null), "KEY-SN-8"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(balanceRepository, serialNumberRepository);
    }

    // ── G4-3: absolute stock count ─────────────────────────────────────────

    private StockBalance position(Item item, Warehouse warehouse, InventoryLot lot, String quantity,
                                  String reserved, long version) {
        StockBalance balance = StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .quantity(new BigDecimal(quantity))
                .reservedQuantity(new BigDecimal(reserved))
                .build();
        balance.setVersion(version);
        return balance;
    }

    private void stubCountMasters(Item item, Warehouse warehouse) {
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouse.getWarehouseId())).thenReturn(Optional.of(warehouse));
    }

    @Test
    @DisplayName("count: decrease writes one ADJUST_OUT for the delta computed from the locked quantity")
    void count_belowCurrent_writesAdjustOutForTheServerComputedDelta() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        stubCountMasters(item, warehouse);
        StockBalance balance = position(item, warehouse, null, "10", "3", 4L);
        when(balanceRepository.lockPositionWithoutLot(item.getItemId(), warehouse.getWarehouseId()))
                .thenReturn(Optional.of(balance));
        when(balanceRepository.saveAndFlush(balance)).thenReturn(balance);
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryCountResult result = service.count(new InventoryCountCommand(
                item.getItemId(), warehouse.getWarehouseId(), null, new BigDecimal("8"), 4L, "Cycle count"), "COUNT-1");

        assertThat(balance.getQuantity()).isEqualByComparingTo("8");
        assertThat(result.previousQuantity()).isEqualByComparingTo("10");
        StockMovement movement = result.movement();
        assertThat(movement.getMovementType()).isEqualTo(MovementType.ADJUST_OUT);
        assertThat(movement.getDirection()).isEqualTo(MovementDirection.OUT);
        assertThat(movement.getQuantity()).isEqualByComparingTo("2");
        assertThat(movement.getReferenceType()).isEqualTo(InventoryMovementService.COUNT_REFERENCE_TYPE);
        assertThat(movement.getIdempotencyKey()).isEqualTo("COUNT-1");
        assertThat(movement.getPayloadHash()).isNotNull();
    }

    @Test
    @DisplayName("count: increase writes one ADJUST_IN")
    void count_aboveCurrent_writesAdjustIn() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        stubCountMasters(item, warehouse);
        StockBalance balance = position(item, warehouse, null, "10", "0", 1L);
        when(balanceRepository.lockPositionWithoutLot(item.getItemId(), warehouse.getWarehouseId()))
                .thenReturn(Optional.of(balance));
        when(balanceRepository.saveAndFlush(balance)).thenReturn(balance);
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryCountResult result = service.count(new InventoryCountCommand(
                item.getItemId(), warehouse.getWarehouseId(), null, new BigDecimal("12.5"), 1L, "Found more"), "COUNT-2");

        assertThat(balance.getQuantity()).isEqualByComparingTo("12.5");
        assertThat(result.movement().getMovementType()).isEqualTo(MovementType.ADJUST_IN);
        assertThat(result.movement().getQuantity()).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("count: a stale expectedVersion is CONCURRENT_MODIFICATION and writes nothing")
    void count_staleVersion_throwsConcurrentModificationBeforeAnyWrite() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        stubCountMasters(item, warehouse);
        StockBalance balance = position(item, warehouse, null, "10", "0", 5L);
        when(balanceRepository.lockPositionWithoutLot(item.getItemId(), warehouse.getWarehouseId()))
                .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.count(new InventoryCountCommand(
                item.getItemId(), warehouse.getWarehouseId(), null, new BigDecimal("8"), 4L, "Cycle count"), "COUNT-3"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));

        assertThat(balance.getQuantity()).isEqualByComparingTo("10");
        verify(balanceRepository, never()).saveAndFlush(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("count: equal to the current quantity is NO_CHANGE and writes nothing")
    void count_equalToCurrent_throwsNoChange() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        stubCountMasters(item, warehouse);
        when(balanceRepository.lockPositionWithoutLot(item.getItemId(), warehouse.getWarehouseId()))
                .thenReturn(Optional.of(position(item, warehouse, null, "10.000000", "0", 2L)));

        assertThatThrownBy(() -> service.count(new InventoryCountCommand(
                item.getItemId(), warehouse.getWarehouseId(), null, new BigDecimal("10"), 2L, "Same"), "COUNT-4"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.NO_CHANGE));

        verify(balanceRepository, never()).saveAndFlush(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("count: below the reserved quantity is RESERVED_STOCK and writes nothing")
    void count_belowReserved_throwsReservedStock() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        stubCountMasters(item, warehouse);
        when(balanceRepository.lockPositionWithoutLot(item.getItemId(), warehouse.getWarehouseId()))
                .thenReturn(Optional.of(position(item, warehouse, null, "10", "3", 2L)));

        assertThatThrownBy(() -> service.count(new InventoryCountCommand(
                item.getItemId(), warehouse.getWarehouseId(), null, new BigDecimal("2"), 2L, "Lost"), "COUNT-5"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESERVED_STOCK));

        verify(balanceRepository, never()).saveAndFlush(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("count: a lot-tracked item without lotId is LOT_REQUIRED before any lock")
    void count_lotTrackedWithoutLot_throwsLotRequired() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        stubCountMasters(item, warehouse);

        assertThatThrownBy(() -> service.count(new InventoryCountCommand(
                item.getItemId(), warehouse.getWarehouseId(), null, new BigDecimal("2"), 0L, "Count"), "COUNT-6"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.LOT_REQUIRED));

        verifyNoInteractions(balanceRepository);
    }

    @Test
    @DisplayName("count: replaying the same key returns the original movement without locking again")
    void count_replay_returnsTheOriginalMovementWithoutANewCount() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, false, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        InventoryCountCommand command = new InventoryCountCommand(
                item.getItemId(), warehouse.getWarehouseId(), null, new BigDecimal("8"), 4L, "Cycle count");
        StockMovement original = StockMovement.builder()
                .item(item).warehouse(warehouse)
                .movementType(MovementType.ADJUST_OUT).direction(MovementDirection.OUT)
                .quantity(new BigDecimal("2"))
                .idempotencyKey("COUNT-7")
                .payloadHash(idempotency.payloadHash(command))
                .build();
        when(movementRepository.findByIdempotencyKeyAndMovementType("COUNT-7", MovementType.ADJUST_IN))
                .thenReturn(Optional.empty());
        when(movementRepository.findByIdempotencyKeyAndMovementType("COUNT-7", MovementType.ADJUST_OUT))
                .thenReturn(Optional.of(original));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(
                item.getItemId(), warehouse.getWarehouseId()))
                .thenReturn(Optional.of(position(item, warehouse, null, "8", "0", 5L)));

        InventoryCountResult result = service.count(command, "COUNT-7");

        assertThat(result.movement()).isSameAs(original);
        assertThat(result.previousQuantity()).isEqualByComparingTo("10");
        verify(balanceRepository, never()).lockPositionWithoutLot(any(), any());
        verify(movementRepository, never()).save(any());
    }

    // ── G4: manual issue refuses an expired lot; CF-2: work-order issue now goes through issueManual too ──

    @Test
    @DisplayName("issueManual: an AVAILABLE lot past its expiry is LOT_NOT_ELIGIBLE and stock is untouched")
    void issueManual_expiredLot_throwsLotNotEligible() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        InventoryLot lot = InventoryLot.builder().lotId(UUID.randomUUID()).item(item).lotCode("OLD")
                .status(LotStatus.AVAILABLE).expiresAt(Instant.now().minusSeconds(60)).build();
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouse.getWarehouseId())).thenReturn(Optional.of(warehouse));
        when(lotRepository.findById(lot.getLotId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> service.issueManual(new InventoryIssueCommand(
                item.getItemId(), warehouse.getWarehouseId(), lot.getLotId(), null, BigDecimal.ONE,
                null, null, null, null, null), "ISSUE-EXP"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOT_NOT_ELIGIBLE));

        verifyNoInteractions(balanceRepository);
    }

    @Test
    @DisplayName("issue (internal flows, e.g. QC): the expiry gate is not applied — since CF-2 work-order issue uses issueManual")
    void issue_internalPath_stillIssuesFromAnExpiredAvailableLot() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        InventoryLot lot = InventoryLot.builder().lotId(UUID.randomUUID()).item(item).lotCode("OLD")
                .status(LotStatus.AVAILABLE).expiresAt(Instant.now().minusSeconds(60)).build();
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouse.getWarehouseId())).thenReturn(Optional.of(warehouse));
        when(lotRepository.findById(lot.getLotId())).thenReturn(Optional.of(lot));
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                item.getItemId(), warehouse.getWarehouseId(), lot.getLotId()))
                .thenReturn(Optional.of(position(item, warehouse, lot, "5", "0", 0L)));
        when(balanceRepository.save(any(StockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementResult result = service.issue(new InventoryIssueCommand(
                item.getItemId(), warehouse.getWarehouseId(), lot.getLotId(), null, BigDecimal.ONE,
                null, "WORK_ORDER", "WO-1", null, null), "ISSUE-WO");

        assertThat(result.created()).isTrue();
    }

    @Test
    @DisplayName("CF-2: issueReservedManual refuses an expired lot (work-order issue from a reservation) and touches no stock")
    void issueReservedManual_expiredLot_throwsLotNotEligible() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        InventoryLot lot = InventoryLot.builder().lotId(UUID.randomUUID()).item(item).lotCode("OLD")
                .status(LotStatus.AVAILABLE).expiresAt(Instant.now().minusSeconds(1)).build();
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouse.getWarehouseId())).thenReturn(Optional.of(warehouse));
        when(lotRepository.findById(lot.getLotId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> service.issueReservedManual(new InventoryIssueCommand(
                item.getItemId(), warehouse.getWarehouseId(), lot.getLotId(), null, BigDecimal.ONE,
                null, "WORK_ORDER", "WO-1", null, null), "ISSUE-RES-EXP"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOT_NOT_ELIGIBLE));

        verifyNoInteractions(balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("CF-2: a replay of an issue already posted is answered from the ledger before the expiry check")
    void issueManual_replayOfAnIssuePostedBeforeTheLotExpired_returnsTheStoredMovement() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        InventoryLot lot = InventoryLot.builder().lotId(UUID.randomUUID()).item(item).lotCode("OLD")
                .status(LotStatus.AVAILABLE).expiresAt(Instant.now().minusSeconds(60)).build();
        StockMovement stored = StockMovement.builder().movementId(UUID.randomUUID()).item(item).warehouse(warehouse)
                .lot(lot).movementType(MovementType.ISSUE).direction(MovementDirection.OUT).quantity(BigDecimal.ONE)
                .idempotencyKey("WO-KEY:L1").build();
        when(movementRepository.findByIdempotencyKeyAndMovementType("WO-KEY:L1", MovementType.ISSUE))
                .thenReturn(Optional.of(stored));

        InventoryMovementResult result = service.issueManual(new InventoryIssueCommand(
                item.getItemId(), warehouse.getWarehouseId(), lot.getLotId(), null, BigDecimal.ONE,
                null, "WORK_ORDER", "WO-1", null, null), "WO-KEY:L1");

        assertThat(result.created()).isFalse();
        assertThat(result.movement()).isSameAs(stored);
        verifyNoInteractions(itemRepository, lotRepository, balanceRepository);
        verify(movementRepository, never()).save(any());
    }

    // ── G4-4: expiresAt on receipt ─────────────────────────────────────────

    @Test
    @DisplayName("receive: expiresAt is stored on a lot the receipt creates")
    void receive_newLotWithExpiry_storesTheExpiry() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        Instant expiry = Instant.parse("2030-12-31T16:59:59Z");
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouse.getWarehouseId())).thenReturn(Optional.of(warehouse));
        when(lotRepository.findByItemItemIdAndLotCode(item.getItemId(), "NEW-LOT")).thenReturn(Optional.empty());
        when(lotRepository.save(any(InventoryLot.class))).thenAnswer(invocation -> {
            InventoryLot lot = invocation.getArgument(0);
            lot.setLotId(UUID.randomUUID());
            return lot;
        });
        when(balanceRepository.save(any(StockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.receive(new InventoryReceiveCommand(item.getItemId(), warehouse.getWarehouseId(), null, "NEW-LOT",
                BigDecimal.ONE, null, null, null, null, null, expiry), "RCV-EXP");

        ArgumentCaptor<InventoryLot> lotCaptor = ArgumentCaptor.forClass(InventoryLot.class);
        verify(lotRepository).save(lotCaptor.capture());
        assertThat(lotCaptor.getValue().getExpiresAt()).isEqualTo(expiry);
    }

    @Test
    @DisplayName("receive: expiresAt on an existing lot is refused — a receipt must not rewrite another lot's expiry")
    void receive_existingLotWithExpiry_isRefusedBeforeAnyWrite() {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(warehouseRepository.findById(warehouse.getWarehouseId())).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.receive(new InventoryReceiveCommand(item.getItemId(),
                warehouse.getWarehouseId(), UUID.randomUUID(), null, BigDecimal.ONE, null, null, null, null, null,
                Instant.parse("2030-12-31T00:00:00Z")), "RCV-EXP-2"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(balanceRepository);
        verify(lotRepository, never()).save(any());
    }

    @Test
    @DisplayName("receive: a null expiresAt keeps the payload fingerprint of commands stored before G4")
    void receiveCommand_nullExpiry_isLeftOutOfThePayloadHash() {
        IdempotencySupport support = new IdempotencySupport(new ObjectMapper());
        String withNull = support.payloadHash(new InventoryReceiveCommand(UUID.fromString(
                "00000000-0000-4000-8000-000000000001"), UUID.fromString("00000000-0000-4000-8000-000000000002"),
                null, "L1", BigDecimal.ONE, null, null, null, null, null, null));
        String json = "{\"itemId\":\"00000000-0000-4000-8000-000000000001\","
                + "\"warehouseId\":\"00000000-0000-4000-8000-000000000002\",\"lotId\":null,\"lotCode\":\"L1\","
                + "\"quantity\":1,\"reason\":null,\"referenceType\":null,\"referenceId\":null,"
                + "\"serialId\":null,\"serialCode\":null}";
        assertThat(withNull).isEqualTo(sha256(json));
    }

    private static String sha256(String text) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── G7c: splitLot ──────────────────────────────────────────────────────

    private record SplitFixture(Item item, Warehouse warehouse, InventoryLot parent, StockBalance parentBalance) {}

    private SplitFixture splitFixture(String parentQuantity) {
        UUID companyId = UUID.randomUUID();
        Item item = item(UUID.randomUUID(), companyId, true, ItemStatus.ACTIVE);
        Warehouse warehouse = warehouse(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        InventoryLot parent = InventoryLot.builder().lotId(UUID.randomUUID()).item(item).lotCode("LOT-1")
                .status(LotStatus.HOLD).receivedAt(java.time.Instant.parse("2026-10-18T01:00:00Z"))
                .expiresAt(java.time.Instant.parse("2026-12-16T17:00:00Z")).build();
        StockBalance parentBalance = StockBalance.builder().item(item).warehouse(warehouse).lot(parent)
                .quantity(new BigDecimal(parentQuantity)).build();
        lenient().when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        lenient().when(warehouseRepository.findById(warehouse.getWarehouseId())).thenReturn(Optional.of(warehouse));
        lenient().when(lotRepository.findById(parent.getLotId())).thenReturn(Optional.of(parent));
        lenient().when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                item.getItemId(), warehouse.getWarehouseId(), parent.getLotId())).thenReturn(Optional.of(parentBalance));
        lenient().when(lotRepository.save(any(InventoryLot.class))).thenAnswer(inv -> {
            InventoryLot saved = inv.getArgument(0);
            if (saved.getLotId() == null) {
                saved.setLotId(UUID.randomUUID());
            }
            return saved;
        });
        lenient().when(movementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        return new SplitFixture(item, warehouse, parent, parentBalance);
    }

    @Test
    @DisplayName("G7c splitLot: moves the quantity into a child lot that inherits expiry, records parent + source, on-hand unchanged")
    void splitLot_movesQuantityIntoChild_andWritesPairedMovements() {
        SplitFixture f = splitFixture("0.100001");
        UUID decisionId = UUID.randomUUID();
        when(lotRepository.existsByItemItemIdAndLotCodeIgnoreCase(f.item().getItemId(), "LOT-1-A1")).thenReturn(false);

        LotSplitResult result = service.splitLot(new LotSplitCommand(f.item().getItemId(), f.warehouse().getWarehouseId(),
                f.parent().getLotId(), "LOT-1-A1", LotStatus.AVAILABLE, new BigDecimal("0.05"),
                "PURCHASE_QC_DECISION", decisionId, "ok"), "PQC-1:A");

        InventoryLot child = result.childLot();
        assertThat(child.getLotCode()).isEqualTo("LOT-1-A1");
        assertThat(child.getStatus()).isEqualTo(LotStatus.AVAILABLE);
        assertThat(child.getParentLot()).isSameAs(f.parent());
        assertThat(child.getExpiresAt()).isEqualTo(f.parent().getExpiresAt());
        assertThat(child.getSourceReferenceType()).isEqualTo("PURCHASE_QC_DECISION");
        assertThat(child.getSourceReferenceId()).isEqualTo(decisionId);
        assertThat(f.parentBalance().getQuantity()).isEqualByComparingTo("0.050001");

        ArgumentCaptor<StockBalance> balances = ArgumentCaptor.forClass(StockBalance.class);
        verify(balanceRepository, times(2)).save(balances.capture());
        StockBalance childBalance = balances.getAllValues().get(1);
        assertThat(childBalance.getLot()).isSameAs(child);
        assertThat(childBalance.getQuantity()).isEqualByComparingTo("0.05");
        // On-hand of the item in the warehouse: 0.050001 + 0.05 = the original 0.100001.
        assertThat(f.parentBalance().getQuantity().add(childBalance.getQuantity())).isEqualByComparingTo("0.100001");

        assertThat(result.outMovement().getMovementType()).isEqualTo(MovementType.LOT_SPLIT_OUT);
        assertThat(result.outMovement().getDirection()).isEqualTo(MovementDirection.OUT);
        assertThat(result.outMovement().getLot()).isSameAs(f.parent());
        assertThat(result.inMovement().getMovementType()).isEqualTo(MovementType.LOT_SPLIT_IN);
        assertThat(result.inMovement().getDirection()).isEqualTo(MovementDirection.IN);
        assertThat(result.inMovement().getLot()).isSameAs(child);
        assertThat(result.inMovement().getReferenceId()).isEqualTo(decisionId.toString());
        assertThat(result.inMovement().getIdempotencyKey()).isEqualTo("PQC-1:A");
    }

    @Test
    @DisplayName("G7c splitLot: a child code already used by the item (any case) is LOT_CODE_ALREADY_EXISTS, nothing moves")
    void splitLot_existingChildCode_isRefused() {
        SplitFixture f = splitFixture("10");
        when(lotRepository.existsByItemItemIdAndLotCodeIgnoreCase(f.item().getItemId(), "LOT-1-A1")).thenReturn(true);

        assertThatThrownBy(() -> service.splitLot(new LotSplitCommand(f.item().getItemId(), f.warehouse().getWarehouseId(),
                f.parent().getLotId(), "LOT-1-A1", LotStatus.AVAILABLE, BigDecimal.ONE, "PURCHASE_QC_DECISION",
                UUID.randomUUID(), null), "PQC-2:A"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.LOT_CODE_ALREADY_EXISTS));
        verify(lotRepository, never()).save(any());
        verify(balanceRepository, never()).save(any());
        assertThat(f.parentBalance().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("G7c splitLot: more than the parent holds is INSUFFICIENT_STOCK, no child is created")
    void splitLot_moreThanTheParentHolds_isRefused() {
        SplitFixture f = splitFixture("1");
        when(lotRepository.existsByItemItemIdAndLotCodeIgnoreCase(any(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.splitLot(new LotSplitCommand(f.item().getItemId(), f.warehouse().getWarehouseId(),
                f.parent().getLotId(), "LOT-1-R1", LotStatus.REJECTED, new BigDecimal("1.000001"), "PURCHASE_QC_DECISION",
                UUID.randomUUID(), "bad"), "PQC-3:R"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));
        verify(lotRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("G7c splitLot: stock reserved on the parent lot cannot be split away — only quantity − reserved")
    void splitLot_reservedPartOfTheParent_isNotSplittable() {
        SplitFixture f = splitFixture("10");
        f.parentBalance().reserve(new BigDecimal("8"));
        when(lotRepository.existsByItemItemIdAndLotCodeIgnoreCase(any(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.splitLot(new LotSplitCommand(f.item().getItemId(), f.warehouse().getWarehouseId(),
                f.parent().getLotId(), "LOT-1-A1", LotStatus.AVAILABLE, new BigDecimal("3"), "PURCHASE_QC_DECISION",
                UUID.randomUUID(), null), "PQC-4:A"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));
        assertThat(f.parentBalance().getQuantity()).isEqualByComparingTo("10");
        verify(movementRepository, never()).save(any());
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

    private Item serialItem(UUID itemId, UUID companyId, ItemStatus status) {
        return Item.builder()
                .itemId(itemId)
                .company(company(companyId))
                .code("SN-ITEM")
                .name("Router")
                .type(ItemType.FINISHED_GOOD)
                .unit("EA")
                .serialTracked(true)
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
