package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderLineRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchasingIdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoodsReceiptDraftService (G7b)")
class GoodsReceiptDraftServiceTest {

    private static final String ZONE = "Asia/Ho_Chi_Minh";

    @Mock GoodsReceiptRepository goodsReceiptRepository;
    @Mock jakarta.persistence.EntityManager entityManager;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryMovementService inventoryMovementService;
    @Mock ItemLookupService itemLookupService;
    @Mock PurchasingIdempotencyRecordRepository idempotencyRecordRepository;
    @Mock AuditorAware<UUID> auditorAware;

    GoodsReceiptDraftService service;
    PurchaseOrder order;
    LocalDate today;

    @BeforeEach
    void setUp() {
        IdempotencySupport idempotency = new IdempotencySupport(new ObjectMapper().registerModule(new JavaTimeModule()));
        service = new GoodsReceiptDraftService(goodsReceiptRepository, purchaseOrderRepository, purchaseOrderLineRepository,
                organizationLookupService, new GoodsReceiptPoster(inventoryMovementService, itemLookupService, idempotency, entityManager, purchaseOrderRepository),
                new PurchasingIdempotency(idempotencyRecordRepository, idempotency), new PurchasingMapper(), auditorAware);
        today = LocalDate.now(ZoneId.of(ZONE));
        order = order(PurchaseOrderStatus.CONFIRMED, "40", "0", false);
        lenient().when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        lenient().when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            if (receipt.getGoodsReceiptId() == null) {
                receipt.setGoodsReceiptId(UUID.randomUUID());
            }
            receipt.getLines().forEach(line -> {
                if (line.getGoodsReceiptLineId() == null) {
                    line.setGoodsReceiptLineId(UUID.randomUUID());
                }
            });
            return receipt;
        });
    }

    private PurchaseOrder order(PurchaseOrderStatus status, String ordered, String received, boolean lotTracked) {
        Company company = Company.builder().companyId(UUID.randomUUID()).code("C").name("C").status(OrganizationStatus.ACTIVE).build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").name("P").timezone(ZONE)
                .status(OrganizationStatus.ACTIVE).build();
        Warehouse warehouse = Warehouse.builder().warehouseId(UUID.randomUUID()).plant(plant).code("W").name("Kho NL")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build();
        Supplier supplier = Supplier.builder().supplierId(UUID.randomUUID()).company(company).code("S").name("NCC")
                .status(SupplierStatus.ACTIVE).build();
        Item item = Item.builder().itemId(UUID.randomUUID()).company(company).code("RM").name("RM").type(ItemType.RAW_MATERIAL)
                .unit("KG").lotTracked(lotTracked).status(ItemStatus.ACTIVE).build();
        PurchaseOrder po = PurchaseOrder.builder().purchaseOrderId(UUID.randomUUID()).company(company).plant(plant)
                .warehouse(warehouse).supplier(supplier).purchaseOrderNo("PO-000001").status(status)
                .orderDate(today.minusDays(5)).expectedDate(today).lines(new ArrayList<>()).build();
        po.setVersion(2L);
        po.getLines().add(PurchaseOrderLine.builder().purchaseOrderLineId(UUID.randomUUID()).purchaseOrder(po).item(item)
                .orderedQuantity(new BigDecimal(ordered)).receivedQuantity(new BigDecimal(received))
                .unitPrice(new BigDecimal("10")).currencyCode("VND").expectedDate(today).build());
        return po;
    }

    private UUID lineId() {
        return order.getLines().get(0).getPurchaseOrderLineId();
    }

    private GoodsReceiptDraftCreateRequest create(LocalDate receivedDate, GoodsReceiptDraftLineRequest... lines) {
        return new GoodsReceiptDraftCreateRequest(order.getPurchaseOrderId(), receivedDate, " DEL-1 ", null, List.of(lines));
    }

    private GoodsReceiptDraftLineRequest line(String quantity) {
        return new GoodsReceiptDraftLineRequest(null, lineId(), new BigDecimal(quantity), null, null);
    }

    // ── create ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createDraft: DRAFT with a server number, no stock moved, no lock, key claimed")
    void createDraft_savesWithoutTouchingStock() {
        when(goodsReceiptRepository.nextReceiptNumber()).thenReturn(3L);

        GoodsReceiptResponse response = service.createDraft(create(today, line("25")), "k-1");

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.receiptNo()).isEqualTo("GR-000003");
        assertThat(response.code()).isEqualTo("GR-000003");
        assertThat(response.reference()).isEqualTo("DEL-1");
        assertThat(response.postedAt()).isNull();
        assertThat(response.lines()).singleElement().satisfies(line -> {
            assertThat(line.stockMovementId()).isNull();
            assertThat(line.movementIds()).isEmpty();
            assertThat(line.trackingPolicy()).isEqualTo("NON_TRACKED");
            assertThat(line.unitPrice()).isEqualByComparingTo("10");
        });
        verifyNoInteractions(inventoryMovementService);
        verify(purchaseOrderRepository, never()).lockRowByPurchaseOrderId(any());
        verify(idempotencyRecordRepository).saveAndFlush(argThat(r -> r.getCommand() == PurchasingCommand.GOODS_RECEIPT_CREATE_DRAFT));
        assertThat(order.getLines().get(0).getReceivedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("createDraft: the order must be CONFIRMED/PARTIALLY_RECEIVED")
    void createDraft_orderNotReceivable_isStateConflict() {
        order.setStatus(PurchaseOrderStatus.DRAFT);

        assertThatThrownBy(() -> service.createDraft(create(today, line("1")), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        verify(goodsReceiptRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createDraft: received date before the order date or after today is a field error")
    void createDraft_receivedDateOutOfRange_isAFieldError() {
        assertThatThrownBy(() -> service.createDraft(create(today.minusDays(6), line("1")), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("receivedDate"));
        assertThatThrownBy(() -> service.createDraft(create(today.plusDays(1), line("1")), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("receivedDate"));
    }

    @Test
    @DisplayName("createDraft: two lines of one PO line count together (20 + 21 > 40 open) — the second line is named")
    void createDraft_splitLinesAboveOpen_namesTheLineThatCrossed() {
        assertThatThrownBy(() -> service.createDraft(create(today, line("20"), line("21")), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[1].receivedQuantity");
                });
    }

    @Test
    @DisplayName("createDraft: lot-tracked line without a lot code / non-tracked line with one — field errors")
    void createDraft_trackingShape_isCheckedPerLine() {
        assertThatThrownBy(() -> service.createDraft(create(today,
                new GoodsReceiptDraftLineRequest(null, lineId(), BigDecimal.ONE, "LOT-1", null)), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("lines[0].lotCode"));

        order.getLines().get(0).getItem().setLotTracked(true);
        assertThatThrownBy(() -> service.createDraft(create(today, line("1")), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("lines[0].lotCode");
                });
    }

    @Test
    @DisplayName("createDraft: a lot code the item already has, in any case, is LOT_CODE_ALREADY_EXISTS (FE Q28)")
    void createDraft_existingLotCodeIgnoringCase_isRejected() {
        order.getLines().get(0).getItem().setLotTracked(true);
        when(itemLookupService.lotCodeExistsIgnoreCase(order.getLines().get(0).getItem().getItemId(), "lot-a")).thenReturn(true);

        assertThatThrownBy(() -> service.createDraft(create(today,
                new GoodsReceiptDraftLineRequest(null, lineId(), BigDecimal.ONE, " lot-a ", null)), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.LOT_CODE_ALREADY_EXISTS);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("lines[0].lotCode");
                });
    }

    // ── update ─────────────────────────────────────────────────────────────

    private GoodsReceipt draft(String... quantities) {
        GoodsReceipt receipt = GoodsReceipt.builder().goodsReceiptId(UUID.randomUUID()).purchaseOrder(order)
                .warehouse(order.getWarehouse()).receiptNo("GR-000009").status(GoodsReceiptStatus.DRAFT)
                .receivedDate(today).note("keep").lines(new ArrayList<>()).build();
        receipt.setVersion(1L);
        for (String quantity : quantities) {
            receipt.getLines().add(GoodsReceiptLine.builder().goodsReceiptLineId(UUID.randomUUID()).goodsReceipt(receipt)
                    .purchaseOrderLine(order.getLines().get(0)).item(order.getLines().get(0).getItem())
                    .receivedQuantity(new BigDecimal(quantity)).build());
        }
        lenient().when(goodsReceiptRepository.findWithDetailsByGoodsReceiptId(receipt.getGoodsReceiptId()))
                .thenReturn(Optional.of(receipt));
        return receipt;
    }

    @Test
    @DisplayName("updateDraft: stable line ids — kept, added, removed; null notes keep; version forced")
    void updateDraft_keepsAddsAndRemovesLines() {
        GoodsReceipt receipt = draft("10", "5");
        UUID kept = receipt.getLines().get(0).getGoodsReceiptLineId();
        UUID dropped = receipt.getLines().get(1).getGoodsReceiptLineId();

        service.updateDraft(receipt.getGoodsReceiptId(), new GoodsReceiptUpdateRequest(1L, today, null, null, List.of(
                new GoodsReceiptDraftLineRequest(kept, lineId(), new BigDecimal("12"), null, null),
                new GoodsReceiptDraftLineRequest(null, lineId(), new BigDecimal("3"), null, null))), null);

        assertThat(receipt.getLines()).extracting(GoodsReceiptLine::getGoodsReceiptLineId).contains(kept).doesNotContain(dropped);
        assertThat(receipt.getLines()).hasSize(2);
        assertThat(receipt.getNote()).isEqualTo("keep");
        assertThat(receipt.getUpdatedAt()).isNotNull();
        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    @DisplayName("updateDraft: stale version / not DRAFT are rejected before anything is written")
    void updateDraft_staleOrNotDraft() {
        GoodsReceipt receipt = draft("10");
        GoodsReceiptUpdateRequest request = new GoodsReceiptUpdateRequest(0L, today, null, null, List.of(line("1")));

        assertThatThrownBy(() -> service.updateDraft(receipt.getGoodsReceiptId(), request, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        receipt.setStatus(GoodsReceiptStatus.POSTED);
        assertThatThrownBy(() -> service.updateDraft(receipt.getGoodsReceiptId(),
                new GoodsReceiptUpdateRequest(1L, today, null, null, List.of(line("1"))), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        verify(goodsReceiptRepository, never()).saveAndFlush(any());
    }

    // ── post ───────────────────────────────────────────────────────────────

    private void stubPostLock(GoodsReceipt receipt) {
        when(goodsReceiptRepository.findPurchaseOrderIdByGoodsReceiptId(receipt.getGoodsReceiptId()))
                .thenReturn(Optional.of(order.getPurchaseOrderId()));
        when(purchaseOrderRepository.lockRowByPurchaseOrderId(order.getPurchaseOrderId())).thenReturn(Optional.of(order.getPurchaseOrderId()));
        when(entityManager.find(PurchaseOrder.class, order.getPurchaseOrderId())).thenReturn(order);
    }

    private StockMovement movement(InventoryLot lot) {
        return StockMovement.builder().movementId(UUID.randomUUID()).item(order.getLines().get(0).getItem())
                .warehouse(order.getWarehouse()).lot(lot).quantity(BigDecimal.ONE).build();
    }

    @Test
    @DisplayName("post: lock the order BEFORE loading the receipt; every line on HOLD, new lots only; PO counters move")
    void post_locksFirst_receivesOnHold_andMovesThePurchaseOrder() {
        order.getLines().get(0).getItem().setLotTracked(true);
        GoodsReceipt receipt = draft("25");
        receipt.getLines().get(0).setLotCode("LOT-25");
        receipt.getLines().get(0).setExpiresOn(today.plusDays(30));
        stubPostLock(receipt);
        InventoryLot lot = InventoryLot.builder().lotId(UUID.randomUUID()).lotCode("LOT-25").status(LotStatus.HOLD)
                .item(order.getLines().get(0).getItem()).build();
        when(inventoryMovementService.receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.HOLD), eq(false)))
                .thenReturn(new InventoryMovementResult(movement(lot), true));
        UUID actor = UUID.randomUUID();
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(actor));

        GoodsReceiptResponse response = service.post(receipt.getGoodsReceiptId(), new GoodsReceiptVersionRequest(1L), null);

        InOrder inOrder = inOrder(goodsReceiptRepository, purchaseOrderRepository, entityManager, inventoryMovementService);
        inOrder.verify(goodsReceiptRepository).findPurchaseOrderIdByGoodsReceiptId(receipt.getGoodsReceiptId());
        inOrder.verify(purchaseOrderRepository).lockRowByPurchaseOrderId(order.getPurchaseOrderId());
        inOrder.verify(entityManager).refresh(order);
        inOrder.verify(goodsReceiptRepository).findWithDetailsByGoodsReceiptId(receipt.getGoodsReceiptId());
        // Re-read under the lock, before any open quantity is used (open-session-in-view, smoke G7b B11).
        inOrder.verify(entityManager).refresh(receipt);
        inOrder.verify(inventoryMovementService).receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.HOLD), eq(false));
        // Loaded once, after the lock; the receipt the guard may already hold is re-read by refresh above.
        verify(goodsReceiptRepository, times(1)).findWithDetailsByGoodsReceiptId(receipt.getGoodsReceiptId());
        ArgumentCaptor<InventoryReceiveCommand> command = ArgumentCaptor.forClass(InventoryReceiveCommand.class);
        verify(inventoryMovementService).receive(command.capture(), eq("GR-" + receipt.getGoodsReceiptId() + ":post:L1"),
                eq(LotStatus.HOLD), eq(false));
        assertThat(command.getValue().lotCode()).isEqualTo("LOT-25");
        assertThat(command.getValue().expiresAt())
                .isEqualTo(today.plusDays(30).atStartOfDay(ZoneId.of(ZONE)).toInstant());
        assertThat(response.status()).isEqualTo("POSTED");
        assertThat(receipt.isQualityHeld()).isTrue();
        assertThat(receipt.getPostedBy()).isEqualTo(actor);
        assertThat(response.lines().get(0).lotStatus()).isEqualTo("HOLD");
        // G7c: a posted, on-hold line is all pending; a tracked line's hold is its lot, not a bucket.
        assertThat(response.lines().get(0).pendingQuantity()).isEqualByComparingTo("25");
        assertThat(response.lines().get(0).acceptedQuantity()).isEqualByComparingTo("0");
        assertThat(response.lines().get(0).qualityBucketId()).isNull();
        assertThat(response.purchaseOrderStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(order.getLines().get(0).getReceivedQuantity()).isEqualByComparingTo("25");
        verify(purchaseOrderRepository).saveAndFlush(order);
    }

    @Test
    @DisplayName("post: what another receipt took meanwhile counts — over the remaining open is refused, nothing received")
    void post_overWhatIsStillOpen_atPostTime_isRefused() {
        GoodsReceipt receipt = draft("20");
        order.getLines().get(0).setReceivedQuantity(new BigDecimal("25"));   // another receipt posted 25 of 40
        stubPostLock(receipt);

        assertThatThrownBy(() -> service.post(receipt.getGoodsReceiptId(), new GoodsReceiptVersionRequest(1L), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsEntry("lines[0].receivedQuantity",
                            "exceeds what is still open on the purchase order line: 15");
                });
        verifyNoInteractions(inventoryMovementService);
        assertThat(receipt.getStatus()).isEqualTo(GoodsReceiptStatus.DRAFT);
    }

    @Test
    @DisplayName("post: a POSTED receipt cannot be posted again with a new key — STATE_CONFLICT")
    void post_notDraft_isStateConflict() {
        GoodsReceipt receipt = draft("1");
        receipt.setStatus(GoodsReceiptStatus.POSTED);
        stubPostLock(receipt);

        assertThatThrownBy(() -> service.post(receipt.getGoodsReceiptId(), new GoodsReceiptVersionRequest(1L), "new-key"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    @DisplayName("post: an expiry that has passed by posting day is refused")
    void post_expiredLotAtPostTime_isRefused() {
        order.getLines().get(0).getItem().setLotTracked(true);
        GoodsReceipt receipt = draft("1");
        receipt.getLines().get(0).setLotCode("LOT-X");
        receipt.getLines().get(0).setExpiresOn(today);
        stubPostLock(receipt);

        assertThatThrownBy(() -> service.post(receipt.getGoodsReceiptId(), new GoodsReceiptVersionRequest(1L), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("lines[0].expiresOn"));
        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    @DisplayName("post replay: same key + payload returns the receipt, no lock, nothing received")
    void post_replay_doesNothing() {
        GoodsReceipt receipt = draft("1");
        receipt.setStatus(GoodsReceiptStatus.POSTED);
        GoodsReceiptVersionRequest request = new GoodsReceiptVersionRequest(1L);
        IdempotencySupport idempotency = new IdempotencySupport(new ObjectMapper().registerModule(new JavaTimeModule()));
        when(idempotencyRecordRepository.findByIdempotencyKeyAndCommand("k", PurchasingCommand.GOODS_RECEIPT_POST))
                .thenReturn(Optional.of(PurchasingIdempotencyRecord.builder().resourceId(receipt.getGoodsReceiptId())
                        .payloadHash(idempotency.payloadHash(new PurchasingIdempotency.Fingerprint(receipt.getGoodsReceiptId(), request)))
                        .build()));

        assertThat(service.post(receipt.getGoodsReceiptId(), request, "k").status()).isEqualTo("POSTED");
        verify(purchaseOrderRepository, never()).lockRowByPurchaseOrderId(any());
        verifyNoInteractions(inventoryMovementService);
    }

    // ── reads ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("candidates: one query for the orders, one for their lines; only open lines are returned")
    void candidates_onlyOpenLines_twoQueries() {
        Plant plant = order.getPlant();
        when(organizationLookupService.getActiveCompany(order.getCompany().getCompanyId())).thenReturn(order.getCompany());
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        PurchaseOrderLine closed = PurchaseOrderLine.builder().purchaseOrderLineId(UUID.randomUUID()).purchaseOrder(order)
                .item(order.getLines().get(0).getItem()).orderedQuantity(BigDecimal.TEN).receivedQuantity(BigDecimal.TEN)
                .expectedDate(today).build();
        when(purchaseOrderRepository.searchReceivingCandidates(eq(order.getCompany().getCompanyId()), eq(plant.getPlantId()),
                anyCollection(), eq("po"), any())).thenReturn(new PageImpl<>(List.of(order)));
        when(purchaseOrderLineRepository.findByPurchaseOrderPurchaseOrderIdIn(List.of(order.getPurchaseOrderId())))
                .thenReturn(List.of(order.getLines().get(0), closed));

        var page = service.candidates(order.getCompany().getCompanyId(), plant.getPlantId(), " po ", PageRequest.of(0, 20));

        assertThat(page.content()).singleElement().satisfies(candidate -> {
            assertThat(candidate.purchaseOrderCode()).isEqualTo("PO-000001");
            assertThat(candidate.lines()).extracting(GoodsReceiptCandidateLineResponse::purchaseOrderLineId)
                    .containsExactly(lineId());
            assertThat(candidate.lines().get(0).openQuantity()).isEqualByComparingTo("40");
        });
        verify(purchaseOrderLineRepository, times(1)).findByPurchaseOrderPurchaseOrderIdIn(anyCollection());
    }

    @Test
    @DisplayName("list: receivedDateFrom after receivedDateTo is a field error, no query")
    void list_invertedDates_isAFieldError() {
        when(organizationLookupService.getActiveCompany(order.getCompany().getCompanyId())).thenReturn(order.getCompany());
        when(organizationLookupService.getActivePlant(order.getPlant().getPlantId())).thenReturn(order.getPlant());

        assertThatThrownBy(() -> service.list(order.getCompany().getCompanyId(), order.getPlant().getPlantId(), null, null,
                null, null, today, today.minusDays(1), PageRequest.of(0, 20)))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("receivedDateFrom"));
        verify(goodsReceiptRepository, never()).searchByPlant(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
