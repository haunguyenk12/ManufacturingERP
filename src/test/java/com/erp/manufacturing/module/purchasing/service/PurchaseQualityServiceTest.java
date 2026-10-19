package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.LotSplitCommand;
import com.erp.manufacturing.module.inventory.service.LotSplitResult;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionResult;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptLineRepository;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseQualityDecisionRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchasingIdempotencyRecordRepository;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseQualityService (G7c)")
class PurchaseQualityServiceTest {

    @Mock GoodsReceiptRepository goodsReceiptRepository;
    @Mock GoodsReceiptLineRepository goodsReceiptLineRepository;
    @Mock PurchaseQualityDecisionRepository decisionRepository;
    @Mock InventoryMovementService inventoryMovementService;
    @Mock ItemLookupService itemLookupService;
    @Mock PurchasingIdempotencyRecordRepository idempotencyRecordRepository;
    @Mock UserLookupService userLookupService;
    @Mock AuditorAware<UUID> auditorAware;
    @Mock EntityManager entityManager;

    PurchaseQualityService service;
    GoodsReceipt receipt;
    GoodsReceiptLine line;
    Item item;
    UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        IdempotencySupport idempotency = new IdempotencySupport(new ObjectMapper().registerModule(new JavaTimeModule()));
        service = new PurchaseQualityService(goodsReceiptRepository, goodsReceiptLineRepository, decisionRepository,
                inventoryMovementService, itemLookupService, new PurchasingIdempotency(idempotencyRecordRepository, idempotency),
                userLookupService, auditorAware, entityManager);
        lenient().when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(actor));
        lenient().when(userLookupService.findUsernames(any())).thenReturn(Map.of(actor, "qc.user"));
        lenient().when(decisionRepository.save(any(PurchaseQualityDecision.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(inv -> {
            GoodsReceipt saved = inv.getArgument(0);
            saved.setVersion(saved.getVersion() + 1);
            return saved;
        });
    }

    private void receipt(boolean lotTracked, String received, Instant lotExpiresAt) {
        Company company = Company.builder().companyId(UUID.randomUUID()).code("C").name("C").status(OrganizationStatus.ACTIVE).build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").name("P").timezone("Asia/Ho_Chi_Minh")
                .status(OrganizationStatus.ACTIVE).build();
        Warehouse warehouse = Warehouse.builder().warehouseId(UUID.randomUUID()).plant(plant).code("W").name("Kho NL")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build();
        Supplier supplier = Supplier.builder().supplierId(UUID.randomUUID()).company(company).code("S").name("NCC")
                .status(SupplierStatus.ACTIVE).build();
        item = Item.builder().itemId(UUID.randomUUID()).company(company).code("RM").name("RM").type(ItemType.RAW_MATERIAL)
                .unit("KG").lotTracked(lotTracked).status(ItemStatus.ACTIVE).build();
        PurchaseOrder order = PurchaseOrder.builder().purchaseOrderId(UUID.randomUUID()).company(company).plant(plant)
                .warehouse(warehouse).supplier(supplier).purchaseOrderNo("PO-000001").status(PurchaseOrderStatus.PARTIALLY_RECEIVED)
                .lines(new ArrayList<>()).build();
        receipt = GoodsReceipt.builder().goodsReceiptId(UUID.randomUUID()).purchaseOrder(order).warehouse(warehouse)
                .receiptNo("GR-000001").status(GoodsReceiptStatus.POSTED).receivedDate(LocalDate.now())
                .postedAt(Instant.now()).qualityHeld(true).lines(new ArrayList<>()).build();
        receipt.setVersion(2L);
        InventoryLot lot = lotTracked
                ? InventoryLot.builder().lotId(UUID.randomUUID()).item(item).lotCode("LOT-1").status(LotStatus.HOLD)
                        .expiresAt(lotExpiresAt).build()
                : null;
        line = GoodsReceiptLine.builder().goodsReceiptLineId(UUID.randomUUID()).goodsReceipt(receipt).item(item).lot(lot)
                .lotCode(lot == null ? null : "LOT-1").receivedQuantity(new BigDecimal(received)).build();
        receipt.getLines().add(line);
        lenient().when(goodsReceiptRepository.lockRowByGoodsReceiptId(receipt.getGoodsReceiptId()))
                .thenReturn(Optional.of(receipt.getGoodsReceiptId()));
        lenient().when(entityManager.find(GoodsReceipt.class, receipt.getGoodsReceiptId())).thenReturn(receipt);
    }

    private PurchaseQcDecisionRequest request(long version, String accepted, String rejected, String reason) {
        return new PurchaseQcDecisionRequest(version, new BigDecimal(accepted), new BigDecimal(rejected), reason);
    }

    private LotSplitResult split(LotStatus status, String code) {
        InventoryLot child = InventoryLot.builder().lotId(UUID.randomUUID()).item(item).lotCode(code).status(status).build();
        StockMovement in = StockMovement.builder().movementId(UUID.randomUUID()).build();
        return new LotSplitResult(child, StockMovement.builder().movementId(UUID.randomUUID()).build(), in);
    }

    private static void assertCode(Throwable ex, Object code) {
        assertThat(ex).isInstanceOf(AppException.class);
        assertThat(((AppException) ex).getErrorCode()).isEqualTo(code);
    }

    // ── tracked ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("tracked: locks the receipt row, re-reads it, splits accepted into AVAILABLE and rejected into REJECTED child lots")
    void decide_tracked_locksThenSplitsBothParts() {
        receipt(true, "0.100001", null);
        when(itemLookupService.lotCodeExistsIgnoreCase(eq(item.getItemId()), anyString())).thenReturn(false);
        LotSplitResult accepted = split(LotStatus.AVAILABLE, "LOT-1-A1");
        LotSplitResult rejected = split(LotStatus.REJECTED, "LOT-1-R1");
        when(inventoryMovementService.splitLot(argThat(c -> c != null && c.childStatus() == LotStatus.AVAILABLE), anyString()))
                .thenReturn(accepted);
        when(inventoryMovementService.splitLot(argThat(c -> c != null && c.childStatus() == LotStatus.REJECTED), anyString()))
                .thenReturn(rejected);

        PurchaseQcDecisionResult result = service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(),
                request(2, "0.05", "0.01", " 0.01 KG failed "), "k-qc");

        InOrder inOrder = inOrder(goodsReceiptRepository, entityManager, inventoryMovementService);
        inOrder.verify(goodsReceiptRepository).lockRowByGoodsReceiptId(receipt.getGoodsReceiptId());
        inOrder.verify(entityManager).refresh(receipt);
        inOrder.verify(inventoryMovementService, times(2)).splitLot(any(LotSplitCommand.class), anyString());
        inOrder.verify(goodsReceiptRepository).saveAndFlush(receipt);

        ArgumentCaptor<LotSplitCommand> commands = ArgumentCaptor.forClass(LotSplitCommand.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(inventoryMovementService, times(2)).splitLot(commands.capture(), keys.capture());
        LotSplitCommand a = commands.getAllValues().get(0);
        LotSplitCommand r = commands.getAllValues().get(1);
        assertThat(a.childLotCode()).isEqualTo("LOT-1-A1");
        assertThat(a.quantity()).isEqualByComparingTo("0.05");
        assertThat(a.parentLotId()).isEqualTo(line.getLot().getLotId());
        assertThat(a.sourceReferenceType()).isEqualTo(PurchaseQcLookupService.LOT_SOURCE_TYPE);
        assertThat(r.childLotCode()).isEqualTo("LOT-1-R1");
        assertThat(r.childStatus()).isEqualTo(LotStatus.REJECTED);
        assertThat(r.quantity()).isEqualByComparingTo("0.01");
        assertThat(a.sourceReferenceId()).isEqualTo(result.decision().decisionId()).isEqualTo(r.sourceReferenceId());
        assertThat(keys.getAllValues()).containsExactly(
                "PQC-" + result.decision().decisionId() + ":A", "PQC-" + result.decision().decisionId() + ":R");

        assertThat(result.receiptVersion()).isEqualTo(3L);
        assertThat(result.pendingQuantity()).isEqualByComparingTo("0.040001");
        assertThat(result.acceptedQuantity()).isEqualByComparingTo("0.05");
        assertThat(result.decision().reason()).isEqualTo("0.01 KG failed");
        assertThat(result.decision().decidedByUsername()).isEqualTo("qc.user");
        assertThat(result.decision().childLots()).extracting("lotCode", "status")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("LOT-1-A1", "AVAILABLE"),
                        org.assertj.core.groups.Tuple.tuple("LOT-1-R1", "REJECTED"));
        assertThat(result.decision().movementIds())
                .containsExactly(accepted.inMovement().getMovementId(), rejected.inMovement().getMovementId());
        assertThat(result.decision().qualityBucketId()).isNull();
        verify(inventoryMovementService, never()).releaseQualityHold(any(), any(), any());
    }

    @Test
    @DisplayName("tracked: child codes skip a suffix another lot of the item already uses (ignoring case)")
    void decide_tracked_childCodeSkipsTakenSuffix() {
        receipt(true, "10", null);
        when(itemLookupService.lotCodeExistsIgnoreCase(item.getItemId(), "LOT-1-A1")).thenReturn(true);
        when(itemLookupService.lotCodeExistsIgnoreCase(item.getItemId(), "LOT-1-A2")).thenReturn(false);
        when(inventoryMovementService.splitLot(any(LotSplitCommand.class), anyString()))
                .thenReturn(split(LotStatus.AVAILABLE, "LOT-1-A2"));

        service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(), request(2, "4", "0", null), null);

        verify(inventoryMovementService).splitLot(argThat(c -> c.childLotCode().equals("LOT-1-A2")), anyString());
    }

    @Test
    @DisplayName("tracked: an expired lot cannot be accepted — LOT_NOT_ELIGIBLE on acceptedQuantity, nothing moves")
    void decide_expiredLot_acceptRefused() {
        receipt(true, "10", Instant.now().minus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(),
                request(2, "4", "0", null), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertCode(ex, BusinessErrorCode.LOT_NOT_ELIGIBLE);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("acceptedQuantity");
                });
        verifyNoInteractions(inventoryMovementService, decisionRepository);
        assertThat(line.getAcceptedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("tracked: an expired lot can still be rejected")
    void decide_expiredLot_rejectAllowed() {
        receipt(true, "10", Instant.now().minus(1, ChronoUnit.DAYS));
        when(itemLookupService.lotCodeExistsIgnoreCase(any(), anyString())).thenReturn(false);
        when(inventoryMovementService.splitLot(any(LotSplitCommand.class), anyString()))
                .thenReturn(split(LotStatus.REJECTED, "LOT-1-R1"));

        PurchaseQcDecisionResult result = service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(),
                request(2, "0", "10", "expired"), null);

        assertThat(result.pendingQuantity()).isEqualByComparingTo("0");
        assertThat(result.rejectedQuantity()).isEqualByComparingTo("10");
    }

    // ── non-tracked ────────────────────────────────────────────────────────

    @Test
    @DisplayName("non-tracked: accepted leaves the quality hold, rejected stays on hold — no split, no ledger row")
    void decide_nonTracked_releasesOnlyTheAcceptedPart() {
        receipt(false, "25", null);

        PurchaseQcDecisionResult result = service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(),
                request(2, "20", "5", "5 kg failed"), null);

        verify(inventoryMovementService).releaseQualityHold(item.getItemId(), receipt.getWarehouse().getWarehouseId(),
                new BigDecimal("20"));
        verify(inventoryMovementService, never()).splitLot(any(), any());
        assertThat(result.pendingQuantity()).isEqualByComparingTo("0");
        assertThat(result.decision().childLots()).isEmpty();
        assertThat(result.decision().movementIds()).isEmpty();
        assertThat(result.decision().qualityBucketId()).isEqualTo(line.getGoodsReceiptLineId());
    }

    @Test
    @DisplayName("non-tracked: a reject-only decision touches no stock at all")
    void decide_nonTracked_rejectOnly_touchesNoStock() {
        receipt(false, "25", null);

        service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(), request(2, "0", "5", "bad"), null);

        verifyNoInteractions(inventoryMovementService);
        assertThat(line.getRejectedQuantity()).isEqualByComparingTo("5");
    }

    // ── refusals ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("0 + 0 is a field error on acceptedQuantity before any lock")
    void decide_bothZero_isRejectedBeforeLocking() {
        receipt(false, "25", null);

        assertThatThrownBy(() -> service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(),
                request(2, "0", "0", null), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertCode(ex, ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("acceptedQuantity");
                });
        verify(goodsReceiptRepository, never()).lockRowByGoodsReceiptId(any());
        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    @DisplayName("a rejected quantity without a reason is a field error on reason")
    void decide_rejectWithoutReason_isAFieldError() {
        receipt(false, "25", null);

        assertThatThrownBy(() -> service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(),
                request(2, "0", "5", "  "), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("reason"));
        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    @DisplayName("accepted + rejected above pending is PLANNED_QUANTITY_EXCEEDED — earlier decisions count")
    void decide_abovePending_isRefused() {
        receipt(false, "25", null);
        line.recordQualityDecision(new BigDecimal("20"), BigDecimal.ZERO);

        assertThatThrownBy(() -> service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(),
                request(2, "5", "0.000001", "x"), null))
                .satisfies(ex -> assertCode(ex, BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED));
        verifyNoInteractions(inventoryMovementService);
        verify(decisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("a stale receipt version is CONCURRENT_MODIFICATION and moves nothing")
    void decide_staleVersion_isConcurrentModification() {
        receipt(false, "25", null);

        assertThatThrownBy(() -> service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(),
                request(1, "5", "0", null), null))
                .satisfies(ex -> assertCode(ex, BusinessErrorCode.CONCURRENT_MODIFICATION));
        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    @DisplayName("a receipt that never went on hold (posted before G7b) is STATE_CONFLICT")
    void decide_receiptNotOnHold_isStateConflict() {
        receipt(false, "25", null);
        receipt.setQualityHeld(false);

        assertThatThrownBy(() -> service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(),
                request(2, "5", "0", null), null))
                .satisfies(ex -> assertCode(ex, BusinessErrorCode.STATE_CONFLICT));
        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    @DisplayName("replay of the same key and body returns the first decision and moves nothing again")
    void decide_replay_returnsTheFirstDecision() {
        receipt(false, "25", null);
        PurchaseQualityDecision earlier = PurchaseQualityDecision.builder().decisionId(UUID.randomUUID())
                .goodsReceipt(receipt).goodsReceiptLine(line).acceptedQuantity(new BigDecimal("5"))
                .rejectedQuantity(BigDecimal.ZERO).decidedAt(Instant.now()).decidedBy(actor).build();
        PurchaseQcDecisionRequest body = request(2, "5", "0", null);
        IdempotencySupport idempotency = new IdempotencySupport(new ObjectMapper().registerModule(new JavaTimeModule()));
        when(idempotencyRecordRepository.findByIdempotencyKeyAndCommand("k-1", PurchasingCommand.PURCHASE_QC_DECISION))
                .thenReturn(Optional.of(PurchasingIdempotencyRecord.builder().idempotencyKey("k-1")
                        .command(PurchasingCommand.PURCHASE_QC_DECISION).resourceId(earlier.getDecisionId())
                        .payloadHash(idempotency.payloadHash(new PurchasingIdempotency.Fingerprint(line.getGoodsReceiptLineId(), body)))
                        .build()));
        when(decisionRepository.findWithDetailsByDecisionId(earlier.getDecisionId())).thenReturn(Optional.of(earlier));

        PurchaseQcDecisionResult result = service.decide(receipt.getGoodsReceiptId(), line.getGoodsReceiptLineId(), body, "k-1");

        assertThat(result.decision().decisionId()).isEqualTo(earlier.getDecisionId());
        verifyNoInteractions(inventoryMovementService);
        verify(goodsReceiptRepository, never()).lockRowByGoodsReceiptId(any());
    }

    // ── queue ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("queue: POSTED lines only, filters threaded through; inverted dates are a field error")
    void queue_threadsFilters_andRejectsInvertedDates() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(goodsReceiptLineRepository.searchQualityQueue(eq(companyId), eq(plantId), isNull(), eq("rm"),
                isNull(), isNull(), eq(GoodsReceiptStatus.POSTED), any())).thenReturn(Page.empty());

        service.queue(companyId, plantId, null, " rm ", null, null, PageRequest.of(0, 20));

        verify(goodsReceiptLineRepository).searchQualityQueue(eq(companyId), eq(plantId), isNull(), eq("rm"),
                isNull(), isNull(), eq(GoodsReceiptStatus.POSTED), any());
        assertThatThrownBy(() -> service.queue(companyId, plantId, null, null, LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 1), PageRequest.of(0, 20)))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("receivedDateFrom"));
    }
}
