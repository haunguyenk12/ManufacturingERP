package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.PurchasingIdempotencyRecordRepository;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import org.springframework.data.domain.AuditorAware;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoodsReceiptService tests")
class GoodsReceiptServiceTest {

    @Mock GoodsReceiptRepository goodsReceiptRepository;
    @Mock jakarta.persistence.EntityManager entityManager;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock InventoryMovementService inventoryMovementService;
    @Mock ItemLookupService itemLookupService;
    @Mock PurchasingPermissionGuard purchasingPermissionGuard;
    @Mock AuditorAware<UUID> auditorAware;
    @Mock PurchasingIdempotencyRecordRepository idempotencyRecordRepository;

    GoodsReceiptService service;

    @BeforeEach
    void setUp() {
        IdempotencySupport idempotency = new IdempotencySupport(new ObjectMapper());
        service = new GoodsReceiptService(
                goodsReceiptRepository,
                purchaseOrderRepository,
                inventoryMovementService,
                new PurchasingMapper(),
                idempotency,
                new GoodsReceiptPoster(inventoryMovementService, itemLookupService, idempotency, entityManager, purchaseOrderRepository),
                purchasingPermissionGuard,
                auditorAware,
                new PurchasingIdempotency(idempotencyRecordRepository, idempotency));
        // G7b: reversing a POSTED receipt re-checks PERM_PURCHASE_ORDER_MANAGE in the service; a dedicated
        // test denies it.
        lenient().when(purchasingPermissionGuard.hasReceiptAccess(any(), eq("PERM_PURCHASE_ORDER_MANAGE"), any()))
                .thenReturn(true);
        lenient().when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void postPartialReceipt_callsInventoryReceiveAndUpdatesPoStatus() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED, new BigDecimal("10"), BigDecimal.ZERO);
        PurchaseOrderLine line = order.getLines().get(0);
        StockMovement movement = movement(line.getItem(), order.getWarehouse(), new BigDecimal("4"));
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.empty());
        when(purchaseOrderRepository.lockRowByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order.getPurchaseOrderId()));
        when(entityManager.find(PurchaseOrder.class, order.getPurchaseOrderId())).thenReturn(order);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            if (receipt.getGoodsReceiptId() == null) {
                receipt.setGoodsReceiptId(UUID.randomUUID());
            }
            receipt.getLines().forEach(receiptLine -> receiptLine.setGoodsReceiptLineId(UUID.randomUUID()));
            return receipt;
        });
        // G7b (D4): purchase receipts go in on HOLD, existing lots allowed on this legacy path.
        when(inventoryMovementService.receive(any(InventoryReceiveCommand.class), eq("GR-KEY:L1"), eq(LotStatus.HOLD), eq(true)))
                .thenReturn(new InventoryMovementResult(movement, true));

        GoodsReceiptResponse response = service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", "Receive", List.of(new GoodsReceiptLineRequest(
                line.getPurchaseOrderLineId(), null, null, new BigDecimal("4")))), "GR-KEY");

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).stockMovementId()).isEqualTo(movement.getMovementId());
        assertThat(line.getReceivedQuantity()).isEqualByComparingTo("4");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        verify(inventoryMovementService).receive(argThat(command ->
                command.itemId().equals(line.getItem().getItemId())
                        && command.warehouseId().equals(order.getWarehouse().getWarehouseId())
                        && command.quantity().compareTo(new BigDecimal("4")) == 0
                        && command.referenceType().equals("GOODS_RECEIPT")), eq("GR-KEY:L1"), eq(LotStatus.HOLD), eq(true));
    }

    @Test
    void postFullReceipt_marksPoReceived() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED, new BigDecimal("10"), BigDecimal.ZERO);
        PurchaseOrderLine line = order.getLines().get(0);
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.empty());
        when(purchaseOrderRepository.lockRowByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order.getPurchaseOrderId()));
        when(entityManager.find(PurchaseOrder.class, order.getPurchaseOrderId())).thenReturn(order);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            if (receipt.getGoodsReceiptId() == null) {
                receipt.setGoodsReceiptId(UUID.randomUUID());
            }
            return receipt;
        });
        when(inventoryMovementService.receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.HOLD), eq(true)))
                .thenReturn(new InventoryMovementResult(
                        movement(line.getItem(), order.getWarehouse(), new BigDecimal("10")), true));

        service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", null, List.of(new GoodsReceiptLineRequest(
                line.getPurchaseOrderLineId(), null, null, new BigDecimal("10")))), "GR-KEY");

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.FULLY_RECEIVED);
    }

    @Test
    void postOverRemainingQuantity_failsBeforeInventoryReceive() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED, new BigDecimal("10"), new BigDecimal("8"));
        PurchaseOrderLine line = order.getLines().get(0);
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.empty());
        when(purchaseOrderRepository.lockRowByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order.getPurchaseOrderId()));
        when(entityManager.find(PurchaseOrder.class, order.getPurchaseOrderId())).thenReturn(order);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            receipt.setGoodsReceiptId(UUID.randomUUID());
            return receipt;
        });

        assertThatThrownBy(() -> service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", null, List.of(new GoodsReceiptLineRequest(
                line.getPurchaseOrderLineId(), null, null, new BigDecimal("3")))), "GR-KEY"))
                .isInstanceOf(AppException.class)
                // D7: B27 over-receipt is 409 PLANNED_QUANTITY_EXCEEDED, not 422
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED));

        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    void postDuplicateIdempotency_returnsExistingReceiptWithoutInventoryReceive() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED, new BigDecimal("10"), BigDecimal.ZERO);
        GoodsReceipt existing = GoodsReceipt.builder()
                .goodsReceiptId(UUID.randomUUID())
                .purchaseOrder(order)
                .warehouse(order.getWarehouse())
                .receiptNo("GR-001")
                .status(GoodsReceiptStatus.POSTED)
                .postedAt(Instant.now())
                .idempotencyKey("GR-KEY")
                .lines(new ArrayList<>())
                .build();
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.of(existing));

        GoodsReceiptResponse response = service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", null, List.of(new GoodsReceiptLineRequest(
                order.getLines().get(0).getPurchaseOrderLineId(), null, null, BigDecimal.ONE))), "GR-KEY");

        assertThat(response.goodsReceiptId()).isEqualTo(existing.getGoodsReceiptId());
        verifyNoInteractions(inventoryMovementService, purchaseOrderRepository);
    }

    @Test
    void cancelPosted_shouldCancelAndCreateReversalMovementAndUpdatePOStatus() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.FULLY_RECEIVED, new BigDecimal("10"), new BigDecimal("10"));
        PurchaseOrderLine poLine = order.getLines().get(0);
        Item item = poLine.getItem();
        Warehouse warehouse = order.getWarehouse();

        UUID grId = UUID.randomUUID();
        GoodsReceiptLine grLine = GoodsReceiptLine.builder()
                .goodsReceiptLineId(UUID.randomUUID())
                .purchaseOrderLine(poLine)
                .item(item)
                .receivedQuantity(new BigDecimal("10"))
                .stockMovement(movement(item, warehouse, new BigDecimal("10")))
                .build();

        GoodsReceipt receipt = GoodsReceipt.builder()
                .goodsReceiptId(grId)
                .purchaseOrder(order)
                .warehouse(warehouse)
                .receiptNo("GR-001")
                .status(GoodsReceiptStatus.POSTED)
                .postedAt(Instant.now())
                .idempotencyKey("GR-KEY")
                .lines(new ArrayList<>(List.of(grLine)))
                .build();
        grLine.setGoodsReceipt(receipt);

        when(goodsReceiptRepository.findWithDetailsByGoodsReceiptId(grId)).thenReturn(Optional.of(receipt));
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryMovementService.reverseReceive(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryMovementResult(movement(item, warehouse, new BigDecimal("10")), true));

        GoodsReceiptResponse response = service.cancel(grId, new GoodsReceiptCancelRequest("damaged batch"));

        assertThat(response.status()).isEqualTo(GoodsReceiptStatus.CANCELLED.name());
        assertThat(response.cancelNote()).isEqualTo("damaged batch");
        assertThat(response.cancelledAt()).isNotNull();
        // PO received_quantity should go back to 0 → SENT
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CONFIRMED);
        assertThat(poLine.getReceivedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(inventoryMovementService).reverseReceive(
                eq(item.getItemId()),
                eq(warehouse.getWarehouseId()),
                isNull(),
                eq(new BigDecimal("10")),
                eq("GOODS_RECEIPT_CANCEL"),
                eq(grId.toString()),
                anyString());
        verify(purchaseOrderRepository).saveAndFlush(order);
        verify(goodsReceiptRepository).saveAndFlush(receipt);
    }

    @Test
    void cancelAlreadyCancelled_shouldThrowOperationNotAllowed() {
        UUID grId = UUID.randomUUID();
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED, new BigDecimal("10"), BigDecimal.ZERO);
        GoodsReceipt receipt = GoodsReceipt.builder()
                .goodsReceiptId(grId)
                .purchaseOrder(order)
                .warehouse(order.getWarehouse())
                .receiptNo("GR-001")
                .status(GoodsReceiptStatus.CANCELLED)
                .postedAt(Instant.now())
                .idempotencyKey("GR-KEY")
                .lines(new ArrayList<>())
                .build();

        when(goodsReceiptRepository.findWithDetailsByGoodsReceiptId(grId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.cancel(grId, null))
                .isInstanceOf(AppException.class)
                // D7: receipt status conflict is 409 STATE_CONFLICT, not 422
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(inventoryMovementService, purchaseOrderRepository);
    }

    // ── G7b ────────────────────────────────────────────────────────────────

    private GoodsReceipt receipt(PurchaseOrder order, GoodsReceiptStatus status, boolean qualityHeld,
                                 GoodsReceiptLine... lines) {
        GoodsReceipt receipt = GoodsReceipt.builder()
                .goodsReceiptId(UUID.randomUUID())
                .purchaseOrder(order)
                .warehouse(order.getWarehouse())
                .receiptNo("GR-000001")
                .status(status)
                .receivedDate(LocalDate.now())
                .qualityHeld(qualityHeld)
                .lines(new ArrayList<>(List.of(lines)))
                .build();
        receipt.setVersion(3L);
        for (GoodsReceiptLine line : lines) {
            line.setGoodsReceipt(receipt);
        }
        when(goodsReceiptRepository.findWithDetailsByGoodsReceiptId(receipt.getGoodsReceiptId()))
                .thenReturn(Optional.of(receipt));
        return receipt;
    }

    private GoodsReceiptLine receiptLine(PurchaseOrder order, String quantity, StockMovement movement) {
        return GoodsReceiptLine.builder()
                .goodsReceiptLineId(UUID.randomUUID())
                .purchaseOrderLine(order.getLines().get(0))
                .item(order.getLines().get(0).getItem())
                .receivedQuantity(new BigDecimal(quantity))
                .stockMovement(movement)
                .build();
    }

    @Test
    @DisplayName("G7b legacy post: the order is locked BEFORE its lines are read; the receipt is POSTED on hold")
    void legacyPost_locksTheOrderFirst_andPostsOnHold() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED, new BigDecimal("10"), BigDecimal.ZERO);
        PurchaseOrderLine line = order.getLines().get(0);
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.empty());
        when(purchaseOrderRepository.lockRowByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order.getPurchaseOrderId()));
        when(entityManager.find(PurchaseOrder.class, order.getPurchaseOrderId())).thenReturn(order);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            if (receipt.getGoodsReceiptId() == null) {
                receipt.setGoodsReceiptId(UUID.randomUUID());
            }
            return receipt;
        });
        when(inventoryMovementService.receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.HOLD), eq(true)))
                .thenReturn(new InventoryMovementResult(movement(line.getItem(), order.getWarehouse(), new BigDecimal("2")), true));

        GoodsReceiptResponse response = service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", null, List.of(new GoodsReceiptLineRequest(line.getPurchaseOrderLineId(), null, null,
                new BigDecimal("2")))), "GR-KEY");

        InOrder inOrder = inOrder(purchaseOrderRepository, entityManager);
        inOrder.verify(purchaseOrderRepository).lockRowByPurchaseOrderId(order.getPurchaseOrderId());
        inOrder.verify(entityManager).refresh(order);
        inOrder.verify(purchaseOrderRepository).findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId());
        assertThat(response.status()).isEqualTo("POSTED");
        assertThat(response.receivedDate()).isEqualTo(LocalDate.now(java.time.ZoneId.of(order.getPlant().getTimezone())));
        // saveAndFlush, so the version on the response is the one persisted (§0.39).
        ArgumentCaptor<GoodsReceipt> saved = ArgumentCaptor.forClass(GoodsReceipt.class);
        verify(goodsReceiptRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isQualityHeld()).isTrue();
        verify(purchaseOrderRepository).saveAndFlush(order);
    }

    @Test
    @DisplayName("G7b legacy post: an existing AVAILABLE lot would skip purchase QC — LOT_NOT_ELIGIBLE, nothing received")
    void legacyPost_intoAnAvailableLot_isRejected() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED, new BigDecimal("10"), BigDecimal.ZERO);
        PurchaseOrderLine line = order.getLines().get(0);
        line.getItem().setLotTracked(true);
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.empty());
        when(purchaseOrderRepository.lockRowByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order.getPurchaseOrderId()));
        when(entityManager.find(PurchaseOrder.class, order.getPurchaseOrderId())).thenReturn(order);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            receipt.setGoodsReceiptId(UUID.randomUUID());
            return receipt;
        });
        when(itemLookupService.findLotByCode(line.getItem().getItemId(), "LOT-1")).thenReturn(Optional.of(
                InventoryLot.builder().lotId(UUID.randomUUID()).item(line.getItem()).lotCode("LOT-1")
                        .status(LotStatus.AVAILABLE).build()));

        assertThatThrownBy(() -> service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", null, List.of(new GoodsReceiptLineRequest(line.getPurchaseOrderLineId(), null, "LOT-1",
                BigDecimal.ONE))), "GR-KEY"))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.LOT_NOT_ELIGIBLE);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("lines[0].lotCode");
                });
        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    @DisplayName("G7b cancel DRAFT: CANCELLED, no stock touched, no PO_MANAGE re-check")
    void cancelDraft_movesNothing() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED, new BigDecimal("10"), BigDecimal.ZERO);
        GoodsReceipt receipt = receipt(order, GoodsReceiptStatus.DRAFT, false, receiptLine(order, "4", null));
        when(goodsReceiptRepository.saveAndFlush(receipt)).thenReturn(receipt);

        GoodsReceiptResponse response = service.cancel(receipt.getGoodsReceiptId(),
                new GoodsReceiptCancelRequest("wrong truck", 3L), null);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(receipt.getCancelNote()).isEqualTo("wrong truck");
        verifyNoInteractions(inventoryMovementService, purchaseOrderRepository);
        verify(purchasingPermissionGuard, never()).hasReceiptAccess(any(), any(), any());
    }

    @Test
    @DisplayName("G7b cancel: a stale expectedVersion is CONCURRENT_MODIFICATION")
    void cancel_staleVersion_isConcurrentModification() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED, new BigDecimal("10"), BigDecimal.ZERO);
        GoodsReceipt receipt = receipt(order, GoodsReceiptStatus.DRAFT, false, receiptLine(order, "4", null));

        assertThatThrownBy(() -> service.cancel(receipt.getGoodsReceiptId(), new GoodsReceiptCancelRequest(null, 2L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        assertThat(receipt.getStatus()).isEqualTo(GoodsReceiptStatus.DRAFT);
    }

    @Test
    @DisplayName("G7b cancel POSTED with only GOODS_RECEIPT_MANAGE: a reversal still needs PO_MANAGE — 403, nothing reversed")
    void cancelPosted_withoutPurchaseOrderManage_isDenied() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.PARTIALLY_RECEIVED, new BigDecimal("10"), new BigDecimal("4"));
        GoodsReceipt receipt = receipt(order, GoodsReceiptStatus.POSTED, true,
                receiptLine(order, "4", movement(order.getLines().get(0).getItem(), order.getWarehouse(), new BigDecimal("4"))));
        when(purchasingPermissionGuard.hasReceiptAccess(any(), eq("PERM_PURCHASE_ORDER_MANAGE"), eq(receipt.getGoodsReceiptId())))
                .thenReturn(false);

        assertThatThrownBy(() -> service.cancel(receipt.getGoodsReceiptId(), null, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(inventoryMovementService);
        assertThat(receipt.getStatus()).isEqualTo(GoodsReceiptStatus.POSTED);
    }

    @Test
    @DisplayName("G7b cancel POSTED on hold, no lot: the quality hold is released BEFORE the reversal (on-hand ≥ hold)")
    void cancelPostedOnHold_releasesTheHoldBeforeReversing() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.PARTIALLY_RECEIVED, new BigDecimal("10"), new BigDecimal("4"));
        PurchaseOrderLine orderLine = order.getLines().get(0);
        GoodsReceipt receipt = receipt(order, GoodsReceiptStatus.POSTED, true,
                receiptLine(order, "4", movement(orderLine.getItem(), order.getWarehouse(), new BigDecimal("4"))));
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order));
        when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryMovementService.reverseReceive(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryMovementResult(movement(orderLine.getItem(), order.getWarehouse(), new BigDecimal("4")), true));

        service.cancel(receipt.getGoodsReceiptId(), null, null);

        InOrder inOrder = inOrder(inventoryMovementService);
        inOrder.verify(inventoryMovementService).releaseQualityHold(orderLine.getItem().getItemId(),
                order.getWarehouse().getWarehouseId(), new BigDecimal("4"));
        inOrder.verify(inventoryMovementService).reverseReceive(eq(orderLine.getItem().getItemId()),
                eq(order.getWarehouse().getWarehouseId()), isNull(), eq(new BigDecimal("4")), any(), any(), any());
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("G7b cancel POSTED before G7b (not on hold): no quality hold to release")
    void cancelLegacyPosted_doesNotReleaseAHoldItNeverMade() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.PARTIALLY_RECEIVED, new BigDecimal("10"), new BigDecimal("4"));
        PurchaseOrderLine orderLine = order.getLines().get(0);
        GoodsReceipt receipt = receipt(order, GoodsReceiptStatus.POSTED, false,
                receiptLine(order, "4", movement(orderLine.getItem(), order.getWarehouse(), new BigDecimal("4"))));
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order));
        when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryMovementService.reverseReceive(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryMovementResult(movement(orderLine.getItem(), order.getWarehouse(), new BigDecimal("4")), true));

        service.cancel(receipt.getGoodsReceiptId(), null, null);

        verify(inventoryMovementService, never()).releaseQualityHold(any(), any(), any());
    }

    /** G7a: a CLOSED order gave up its remainder — reversing a receipt would reopen it. */
    @Test
    void cancelPostedReceiptOfClosedOrder_isStateConflictAndReversesNothing() {
        UUID grId = UUID.randomUUID();
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CLOSED, new BigDecimal("10"), new BigDecimal("4"));
        GoodsReceipt receipt = GoodsReceipt.builder()
                .goodsReceiptId(grId)
                .purchaseOrder(order)
                .warehouse(order.getWarehouse())
                .receiptNo("GR-001")
                .status(GoodsReceiptStatus.POSTED)
                .postedAt(Instant.now())
                .idempotencyKey("GR-KEY")
                .lines(new ArrayList<>())
                .build();
        when(goodsReceiptRepository.findWithDetailsByGoodsReceiptId(grId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.cancel(grId, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(inventoryMovementService, purchaseOrderRepository);
        assertThat(receipt.getStatus()).isEqualTo(GoodsReceiptStatus.POSTED);
    }

    private StockMovement movement(Item item, Warehouse warehouse, BigDecimal quantity) {
        return StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(quantity)
                .referenceType("GOODS_RECEIPT")
                .referenceId(UUID.randomUUID().toString())
                .idempotencyKey(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .build();
    }

    private PurchaseOrder purchaseOrder(PurchaseOrderStatus status,
                                        BigDecimal orderedQuantity,
                                        BigDecimal receivedQuantity) {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item item = item(UUID.randomUUID(), company);
        PurchaseOrder order = PurchaseOrder.builder()
                .purchaseOrderId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .supplier(supplier(UUID.randomUUID(), company))
                .purchaseOrderNo("PO-001")
                .status(status)
                .orderDate(LocalDate.now())
                .expectedDate(LocalDate.now().plusDays(7))
                .lines(new ArrayList<>())
                .build();
        order.getLines().add(PurchaseOrderLine.builder()
                .purchaseOrderLineId(UUID.randomUUID())
                .purchaseOrder(order)
                .item(item)
                .orderedQuantity(orderedQuantity)
                .receivedQuantity(receivedQuantity)
                .expectedDate(order.getExpectedDate())
                .build());
        return order;
    }

    private Item item(UUID itemId, Company company) {
        return Item.builder()
                .itemId(itemId)
                .company(company)
                .code("ITEM")
                .name("Item")
                .type(ItemType.RAW_MATERIAL)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private Supplier supplier(UUID supplierId, Company company) {
        return Supplier.builder()
                .supplierId(supplierId)
                .company(company)
                .code("SUP")
                .name("Supplier")
                .status(SupplierStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, Plant plant) {
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant)
                .code("WH1")
                .name("Warehouse 1")
                .type(WarehouseType.GENERAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Plant plant(UUID plantId, Company company) {
        return Plant.builder()
                .plantId(plantId)
                .company(company)
                .code("P1")
                .name("Plant 1")
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
