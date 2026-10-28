package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.StockBalanceAggregateResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.service.InventoryService;
import com.erp.manufacturing.module.inventory.service.InventoryAdjustCommand;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.inventory.service.LotQcOwnershipGuard;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptLineRepository;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseQcHoldProjection;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.SupplierRepository;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.service.query.LotQcOriginLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Purchase QC on a real database (G7c). Only Postgres answers what is checked here (rule R7): the lot
 * split really keeps on-hand constant across parent and child balances, the non-tracked hold really
 * stays for the rejected part, the queue query with every optional filter left out, and two decisions
 * prepared on the same receipt version — with the receipt already in the EntityManager the way the
 * {@code @PreAuthorize} guard leaves it under open-session-in-view (B194) — consume pending only once.
 *
 * <p>The {@code @DataJpaTest} wrapping transaction is off: each call commits on its own, as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PurchaseQualityService.class, GoodsReceiptDraftService.class, GoodsReceiptPoster.class, PurchasingIdempotency.class,
        PurchasingMapper.class, IdempotencySupport.class, TraceIdProvider.class, InventoryMovementService.class,
        ItemLookupService.class, OrganizationLookupService.class, UserLookupService.class, LotQcOwnershipGuard.class,
        PurchaseQcLookupService.class, LotQcOriginLookupService.class, InventoryService.class, InventoryMapper.class,
        PurchaseQualityIT.Config.class})
class PurchaseQualityIT extends AbstractPostgresIntegrationTest {

    private static final String ZONE = "Asia/Ho_Chi_Minh";
    private static final int ROUNDS = 3;

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class Config {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    @Autowired PurchaseQualityService qualityService;
    @Autowired GoodsReceiptDraftService receiptService;
    @Autowired GoodsReceiptRepository receiptRepository;
    @Autowired GoodsReceiptLineRepository receiptLineRepository;
    @Autowired PurchaseOrderRepository orderRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired InventoryMovementService movementService;
    @Autowired LotQcOwnershipGuard lotQcOwnershipGuard;
    @Autowired PurchaseQcLookupService purchaseQcLookupService;
    @Autowired InventoryService inventoryService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    private record Fixture(Company company, Plant plant, Warehouse warehouse, Supplier supplier, String suffix) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("QC_" + suffix).name("Co").status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("P_" + suffix).name("Plant").timezone(ZONE)
                .status(OrganizationStatus.ACTIVE).build());
        Warehouse warehouse = warehouseRepository.save(Warehouse.builder().plant(plant).code("W_" + suffix).name("Wh")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build());
        Supplier supplier = supplierRepository.save(Supplier.builder().company(company).code("S_" + suffix).name("Acme Metal")
                .status(SupplierStatus.ACTIVE).build());
        return new Fixture(company, plant, warehouse, supplier, suffix);
    }

    private Item item(Fixture f, boolean lotTracked) {
        return itemRepository.save(Item.builder().company(f.company()).code("I_" + UUID.randomUUID().toString().substring(0, 8))
                .name("Raw").type(ItemType.RAW_MATERIAL).unit("KG").lotTracked(lotTracked).status(ItemStatus.ACTIVE).build());
    }

    private PurchaseOrder order(Fixture f, Item item, String ordered) {
        return order(f, f.plant(), f.warehouse(), item, ordered);
    }

    private PurchaseOrder order(Fixture f, Plant plant, Warehouse warehouse, Item item, String ordered) {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));
        PurchaseOrder order = PurchaseOrder.builder().company(f.company()).plant(plant).warehouse(warehouse)
                .supplier(f.supplier()).purchaseOrderNo("PO-" + UUID.randomUUID().toString().substring(0, 8))
                .status(PurchaseOrderStatus.CONFIRMED).orderDate(today.minusDays(2)).expectedDate(today).build();
        order.getLines().add(PurchaseOrderLine.builder().purchaseOrder(order).item(item)
                .orderedQuantity(new BigDecimal(ordered)).receivedQuantity(BigDecimal.ZERO)
                .unitPrice(new BigDecimal("10")).currencyCode("VND").expectedDate(today).build());
        return orderRepository.saveAndFlush(order);
    }

    /** A POSTED, on-hold receipt of one line; returns the posted response. */
    private GoodsReceiptResponse posted(PurchaseOrder order, String quantity, String lotCode) {
        UUID orderLineId = jdbc.queryForObject(
                "select purchase_order_line_id from purchase_order_lines where purchase_order_id = ?",
                UUID.class, order.getPurchaseOrderId());
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));
        GoodsReceiptResponse draft = receiptService.createDraft(new GoodsReceiptDraftCreateRequest(order.getPurchaseOrderId(),
                today, null, null, List.of(new GoodsReceiptDraftLineRequest(null, orderLineId, new BigDecimal(quantity), lotCode,
                        lotCode == null ? null : today.plusDays(30)))), null);
        return receiptService.post(draft.goodsReceiptId(), new GoodsReceiptVersionRequest(draft.version()), null);
    }

    private BigDecimal onHand(Item item) {
        return jdbc.queryForObject("select coalesce(sum(quantity), 0) from stock_balances where item_id = ?",
                BigDecimal.class, item.getItemId());
    }

    @Test
    void trackedDecision_splitsIntoChildLots_onHandUnchanged_parentKeepsWhatIsPending() {
        Fixture f = fixture();
        Item item = item(f, true);
        GoodsReceiptResponse receipt = posted(order(f, item, "1"), "0.100001", "LOT-" + f.suffix());
        GoodsReceiptLineResponse line = receipt.lines().get(0);

        PurchaseQcDecisionResult result = qualityService.decide(receipt.goodsReceiptId(), line.goodsReceiptLineId(),
                new PurchaseQcDecisionRequest(receipt.version(), new BigDecimal("0.05"), new BigDecimal("0.01"), "0.01 failed"),
                null);

        assertThat(result.pendingQuantity()).isEqualByComparingTo("0.040001");
        assertThat(result.receiptVersion()).isEqualTo(receipt.version() + 1);
        // The response itself names the parent — not only the stored column (smoke G7c found it null).
        assertThat(result.decision().childLots()).extracting(PurchaseQcChildLotResponse::parentLotId)
                .containsOnly(line.lotId());
        List<Map<String, Object>> lots = jdbc.queryForList("""
                select l.lot_code, l.status, l.parent_lot_id, l.source_reference_type, l.source_reference_id, l.expires_at, b.quantity
                from inventory_lots l join stock_balances b on b.lot_id = l.lot_id
                where l.item_id = ? order by l.lot_code
                """, item.getItemId());
        assertThat(lots).extracting(row -> row.get("lot_code"))
                .containsExactly("LOT-" + f.suffix(), "LOT-" + f.suffix() + "-A1", "LOT-" + f.suffix() + "-R1");
        assertThat((BigDecimal) lots.get(0).get("quantity")).isEqualByComparingTo("0.040001");
        assertThat(lots.get(0).get("status")).isEqualTo("HOLD");
        assertThat((BigDecimal) lots.get(1).get("quantity")).isEqualByComparingTo("0.05");
        assertThat(lots.get(1).get("status")).isEqualTo("AVAILABLE");
        assertThat((BigDecimal) lots.get(2).get("quantity")).isEqualByComparingTo("0.01");
        assertThat(lots.get(2).get("status")).isEqualTo("REJECTED");
        assertThat(lots.get(1).get("parent_lot_id")).isEqualTo(line.lotId());
        assertThat(lots.get(2).get("source_reference_type")).isEqualTo("PURCHASE_QC_DECISION");
        assertThat(lots.get(2).get("source_reference_id")).isEqualTo(result.decision().decisionId());
        assertThat(lots.get(1).get("expires_at")).isEqualTo(lots.get(0).get("expires_at"));
        assertThat(onHand(item)).isEqualByComparingTo("0.100001");
        assertThat(jdbc.queryForObject("""
                select count(*) from stock_movements
                where item_id = ? and movement_type in ('LOT_SPLIT_OUT', 'LOT_SPLIT_IN') and reference_id = ?
                """, Integer.class, item.getItemId(), result.decision().decisionId().toString())).isEqualTo(4);
        Map<String, Object> stored = jdbc.queryForMap(
                "select accepted_quantity, rejected_quantity from goods_receipt_lines where goods_receipt_line_id = ?",
                line.goodsReceiptLineId());
        assertThat((BigDecimal) stored.get("accepted_quantity")).isEqualByComparingTo("0.05");
        assertThat((BigDecimal) stored.get("rejected_quantity")).isEqualByComparingTo("0.01");
        assertThat(receiptLineRepository.existsPendingQualityForLot(line.lotId(), GoodsReceiptStatus.POSTED)).isTrue();

        // A second decision takes what is still pending: the next free suffix, and the lot is no longer awaiting QC.
        PurchaseQcDecisionResult second = qualityService.decide(receipt.goodsReceiptId(), line.goodsReceiptLineId(),
                new PurchaseQcDecisionRequest(result.receiptVersion(), new BigDecimal("0.040001"), BigDecimal.ZERO, null), null);

        assertThat(second.pendingQuantity()).isEqualByComparingTo("0");
        assertThat(second.decision().childLots()).singleElement()
                .satisfies(child -> assertThat(child.lotCode()).isEqualTo("LOT-" + f.suffix() + "-A2"));
        assertThat(receiptLineRepository.existsPendingQualityForLot(line.lotId(), GoodsReceiptStatus.POSTED)).isFalse();
        assertThat(onHand(item)).isEqualByComparingTo("0.100001");
    }

    @Test
    void nonTrackedDecision_releasesAcceptedFromHold_rejectedStaysOnHold_queueDropsTheFinishedLine() {
        Fixture f = fixture();
        Item item = item(f, false);
        GoodsReceiptResponse receipt = posted(order(f, item, "40"), "25", null);
        UUID lineId = receipt.lines().get(0).goodsReceiptLineId();

        var before = qualityService.queue(f.company().getCompanyId(), f.plant().getPlantId(), null, null, null, null,
                PageRequest.of(0, 20));
        assertThat(before.content()).extracting(PurchaseQcQueueLineResponse::goodsReceiptLineId).containsExactly(lineId);
        assertThat(before.content().get(0).qualityBucketId()).isEqualTo(lineId);

        PurchaseQcDecisionResult result = qualityService.decide(receipt.goodsReceiptId(), lineId,
                new PurchaseQcDecisionRequest(receipt.version(), new BigDecimal("20"), new BigDecimal("5"), "5 kg failed"), null);

        assertThat(result.pendingQuantity()).isEqualByComparingTo("0");
        Map<String, Object> balance = jdbc.queryForMap(
                "select quantity, quality_hold_quantity from stock_balances where item_id = ?", item.getItemId());
        assertThat((BigDecimal) balance.get("quantity")).isEqualByComparingTo("25");
        assertThat((BigDecimal) balance.get("quality_hold_quantity")).isEqualByComparingTo("5");
        assertThat(qualityService.queue(f.company().getCompanyId(), f.plant().getPlantId(), null, null, null, null,
                PageRequest.of(0, 20)).content()).isEmpty();
        assertThat(qualityService.decisions(receipt.goodsReceiptId(), PageRequest.of(0, 20)).content())
                .singleElement().satisfies(decision -> {
                    assertThat(decision.acceptedQuantity()).isEqualByComparingTo("20");
                    assertThat(decision.childLots()).isEmpty();
                });
    }

    /**
     * FE A18: two decisions prepared on the same receipt version, each in a transaction that already
     * holds the receipt graph (what the guard leaves under open-session-in-view). The receipt row lock
     * plus the re-read make the second one see the first one's version: exactly one decision is kept,
     * the other is a stale-version 409, and pending is consumed once.
     */
    @Test
    void concurrentDecisions_onTheSameVersion_onlyOneIsKept() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Fixture f = fixture();
            Item item = item(f, false);
            GoodsReceiptResponse receipt = posted(order(f, item, "40"), "10", null);
            UUID lineId = receipt.lines().get(0).goodsReceiptLineId();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CyclicBarrier bothLoaded = new CyclicBarrier(2);
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    try {
                        return transactions.execute(status -> {
                            receiptRepository.findWithDetailsByGoodsReceiptId(receipt.goodsReceiptId())
                                    .ifPresent(r -> r.getLines().forEach(l -> l.getReceivedQuantity()));
                            await(bothLoaded);
                            return qualityService.decide(receipt.goodsReceiptId(), lineId, new PurchaseQcDecisionRequest(
                                    receipt.version(), new BigDecimal("6"), BigDecimal.ZERO, null), null);
                        });
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();

            assertThat(outcomes).filteredOn(PurchaseQcDecisionResult.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(Exception.class::isInstance).singleElement().satisfies(ex -> {
                assertThat(ex).isInstanceOf(AppException.class);
                assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION);
            });
            assertThat(jdbc.queryForObject("select accepted_quantity from goods_receipt_lines where goods_receipt_line_id = ?",
                    BigDecimal.class, lineId)).isEqualByComparingTo("6");
            assertThat(jdbc.queryForObject("select quality_hold_quantity from stock_balances where item_id = ?",
                    BigDecimal.class, item.getItemId())).isEqualByComparingTo("4");
            assertThat(jdbc.queryForObject("select count(*) from purchase_quality_decisions where goods_receipt_line_id = ?",
                    Integer.class, lineId)).isEqualTo(1);
        }
    }

    /**
     * CF-1 (B263) on a real database: once purchase QC owns a lot, a generic adjustment through the
     * real guard is refused — the parent still holding pending quantity and the rejected child — and
     * leaves no ledger row; the accepted child is ordinary stock and adjusts normally.
     */
    @Test
    void adjust_lotsPurchaseQcStillOwns_areRefused_acceptedChildAdjusts() {
        Fixture f = fixture();
        Item item = item(f, true);
        GoodsReceiptResponse receipt = posted(order(f, item, "10"), "4", "LOT-" + f.suffix());
        GoodsReceiptLineResponse line = receipt.lines().get(0);
        PurchaseQcDecisionResult result = qualityService.decide(receipt.goodsReceiptId(), line.goodsReceiptLineId(),
                new PurchaseQcDecisionRequest(receipt.version(), new BigDecimal("2.5"), new BigDecimal("0.5"), "0.5 failed"),
                null);
        UUID parentLotId = line.lotId();
        UUID acceptedLotId = lotId(item, "LOT-" + f.suffix() + "-A1");
        UUID rejectedLotId = lotId(item, "LOT-" + f.suffix() + "-R1");
        assertThat(result.pendingQuantity()).isEqualByComparingTo("1");
        int movementsBefore = movementCount(item);

        assertRefused(item, f, parentLotId, "1", "CF1-P-IN-" + f.suffix());
        assertRefused(item, f, parentLotId, "-1", "CF1-P-OUT-" + f.suffix());
        assertRefused(item, f, rejectedLotId, "-0.5", "CF1-R-" + f.suffix());

        assertThat(movementCount(item)).isEqualTo(movementsBefore);
        assertThat(lotQuantity(parentLotId)).isEqualByComparingTo("1");
        assertThat(lotQuantity(rejectedLotId)).isEqualByComparingTo("0.5");

        var accepted = movementService.adjust(adjustCommand(item, f, acceptedLotId, "-1"), "CF1-A-" + f.suffix(),
                lotQcOwnershipGuard::ensureAdjustable);

        assertThat(accepted.created()).isTrue();
        assertThat(lotQuantity(acceptedLotId)).isEqualByComparingTo("1.5");
        assertThat(movementCount(item)).isEqualTo(movementsBefore + 1);
        assertThat(onHand(item)).isEqualByComparingTo("3");
    }

    /** CF-1: the guard is narrow — a lot an operator put on HOLD outside any receipt stays adjustable. */
    @Test
    void adjust_lotHeldWithoutAnyReceipt_staysAdjustable() {
        Fixture f = fixture();
        Item item = item(f, true);
        String lotCode = "HAND-" + f.suffix();
        movementService.receive(new InventoryReceiveCommand(item.getItemId(), f.warehouse().getWarehouseId(), null, lotCode,
                new BigDecimal("3"), "Opening stock", null, null, null, null, null), "CF1-RCV-" + f.suffix(), LotStatus.HOLD);
        UUID lotId = lotId(item, lotCode);
        assertThat(jdbc.queryForObject("select status from inventory_lots where lot_id = ?", String.class, lotId)).isEqualTo("HOLD");

        var adjusted = movementService.adjust(adjustCommand(item, f, lotId, "-1"), "CF1-HAND-" + f.suffix(),
                lotQcOwnershipGuard::ensureAdjustable);

        assertThat(adjusted.created()).isTrue();
        assertThat(lotQuantity(lotId)).isEqualByComparingTo("2");
    }

    /**
     * CF-3 on a real database: {@code sumNonLotQualityHold} sums pending (received − accepted − rejected) and rejected
     * of non-lot lines of POSTED, on-hold receipts, per item and warehouse — over partial and repeated decisions on
     * several receipts. A lot line, a DRAFT, a CANCELLED and a pre-G7b ({@code quality_held = false}) receipt, another
     * warehouse (in another plant) and an item not asked for are all left out.
     */
    @Test
    void sumNonLotQualityHold_sumsPendingAndRejectedOfOnHoldPostedNonLotLines_only() {
        Fixture f = fixture();
        Plant plantB = plantRepository.save(Plant.builder().company(f.company()).code("PB_" + f.suffix()).name("Plant B")
                .timezone(ZONE).status(OrganizationStatus.ACTIVE).build());
        Warehouse warehouseB = warehouseRepository.save(Warehouse.builder().plant(plantB).code("WB_" + f.suffix())
                .name("Wh B").type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build());
        Item itemX = item(f, false);
        Item itemY = item(f, false);
        Item lotItem = item(f, true);
        UUID whA = f.warehouse().getWarehouseId();
        UUID whB = warehouseB.getWarehouseId();

        // Receipt 1, item X @ A: 25 received, decided twice (10/5 then 3/2) => pending 5, rejected 7.
        GoodsReceiptResponse r1 = posted(order(f, itemX, "40"), "25", null);
        UUID r1Line = r1.lines().get(0).goodsReceiptLineId();
        PurchaseQcDecisionResult d1 = qualityService.decide(r1.goodsReceiptId(), r1Line,
                new PurchaseQcDecisionRequest(r1.version(), new BigDecimal("10"), new BigDecimal("5"), "5 failed"), null);
        qualityService.decide(r1.goodsReceiptId(), r1Line,
                new PurchaseQcDecisionRequest(d1.receiptVersion(), new BigDecimal("3"), new BigDecimal("2"), "2 failed"), null);
        // Receipt 2, item X @ A: 8 received, decided 0/1 => pending 7, rejected 1.
        GoodsReceiptResponse r2 = posted(order(f, itemX, "40"), "8", null);
        qualityService.decide(r2.goodsReceiptId(), r2.lines().get(0).goodsReceiptLineId(),
                new PurchaseQcDecisionRequest(r2.version(), BigDecimal.ZERO, new BigDecimal("1"), "1 failed"), null);
        // Receipt 3, item X @ A, untouched: pending 0.5.
        posted(order(f, itemX, "40"), "0.5", null);

        // Excluded: a DRAFT, a CANCELLED and a pre-G7b receipt of item X @ A.
        PurchaseOrder draftOrder = order(f, itemX, "40");
        receiptService.createDraft(new GoodsReceiptDraftCreateRequest(draftOrder.getPurchaseOrderId(),
                LocalDate.now(ZoneId.of(ZONE)), null, null, List.of(new GoodsReceiptDraftLineRequest(null,
                orderLineId(draftOrder), new BigDecimal("4"), null, null))), null);
        GoodsReceiptResponse cancelled = posted(order(f, itemX, "40"), "6", null);
        jdbc.update("update goods_receipts set status = 'CANCELLED', cancelled_at = now() where goods_receipt_id = ?", cancelled.goodsReceiptId());
        GoodsReceiptResponse legacy = posted(order(f, itemX, "40"), "9", null);
        jdbc.update("update goods_receipts set quality_held = false where goods_receipt_id = ?", legacy.goodsReceiptId());

        // Item X @ B (another plant): pending 11, kept apart from A.
        posted(order(f, plantB, warehouseB, itemX, "40"), "11", null);
        // Item Y @ A: pending 3, only when asked for.
        posted(order(f, itemY, "40"), "3", null);
        // A lot line of a lot-tracked item @ A: its quantity lives on the lot, never in this sum.
        posted(order(f, lotItem, "40"), "2", "LOT-" + f.suffix());

        Map<String, PurchaseQcHoldProjection> onlyA = byItemAndWarehouse(receiptLineRepository.sumNonLotQualityHold(
                List.of(itemX.getItemId(), lotItem.getItemId()), List.of(whA), GoodsReceiptStatus.POSTED));
        assertThat(onlyA).containsOnlyKeys(itemX.getItemId() + "@" + whA);
        assertThat(onlyA.get(itemX.getItemId() + "@" + whA).getPendingQuantity()).isEqualByComparingTo("12.5");
        assertThat(onlyA.get(itemX.getItemId() + "@" + whA).getRejectedQuantity()).isEqualByComparingTo("8");

        Map<String, PurchaseQcHoldProjection> both = byItemAndWarehouse(receiptLineRepository.sumNonLotQualityHold(
                List.of(itemX.getItemId(), itemY.getItemId(), lotItem.getItemId()), List.of(whA, whB),
                GoodsReceiptStatus.POSTED));
        assertThat(both).containsOnlyKeys(itemX.getItemId() + "@" + whA, itemX.getItemId() + "@" + whB,
                itemY.getItemId() + "@" + whA);
        assertThat(both.get(itemX.getItemId() + "@" + whA).getPendingQuantity()).isEqualByComparingTo("12.5");
        assertThat(both.get(itemX.getItemId() + "@" + whB).getPendingQuantity()).isEqualByComparingTo("11");
        assertThat(both.get(itemX.getItemId() + "@" + whB).getRejectedQuantity()).isEqualByComparingTo("0");
        assertThat(both.get(itemY.getItemId() + "@" + whA).getPendingQuantity()).isEqualByComparingTo("3");

        // The lookup the aggregate screen uses: one warehouse, keyed by item.
        Map<UUID, PurchaseQcLookupService.QualityHold> holds = purchaseQcLookupService.nonLotQualityHoldByItem(
                whA, List.of(itemX.getItemId(), itemY.getItemId(), lotItem.getItemId()));
        assertThat(holds).containsOnlyKeys(itemX.getItemId(), itemY.getItemId());
        assertThat(holds.get(itemX.getItemId()).pendingQuantity()).isEqualByComparingTo("12.5");
        assertThat(holds.get(itemX.getItemId()).rejectedQuantity()).isEqualByComparingTo("8");
    }

    /**
     * CF-3 end to end (FE fixture): a non-tracked receipt of 25 goes on hold; QC 10/5 then 10/0. The aggregate row's
     * quality hold is fully explained by purchase QC at each step — pending and rejected, nothing unclassified.
     */
    @Test
    void aggregateBalance_breaksTheQualityHoldDownIntoPendingAndRejectedPurchaseQc() {
        Fixture f = fixture();
        Item item = item(f, false);
        GoodsReceiptResponse receipt = posted(order(f, item, "40"), "25", null);
        UUID lineId = receipt.lines().get(0).goodsReceiptLineId();

        StockBalanceAggregateResponse onHold = aggregateRow(f, item);
        assertThat(onHold.pendingPurchaseQcQuantity()).isEqualByComparingTo("25");
        assertThat(onHold.qualityHoldQuantity()).isEqualByComparingTo("25");
        assertThat(onHold.availableQuantity()).isEqualByComparingTo("0");
        assertThat(onHold.qualityBreakdownKnown()).isTrue();

        PurchaseQcDecisionResult first = qualityService.decide(receipt.goodsReceiptId(), lineId,
                new PurchaseQcDecisionRequest(receipt.version(), new BigDecimal("10"), new BigDecimal("5"), "5 kg failed"), null);

        StockBalanceAggregateResponse afterFirst = aggregateRow(f, item);
        assertThat(afterFirst.onHandQuantity()).isEqualByComparingTo("25");
        assertThat(afterFirst.qualityHoldQuantity()).isEqualByComparingTo("15");
        assertThat(afterFirst.availableQuantity()).isEqualByComparingTo("10");
        assertThat(afterFirst.pendingPurchaseQcQuantity()).isEqualByComparingTo("10");
        assertThat(afterFirst.rejectedPurchaseQcQuantity()).isEqualByComparingTo("5");
        assertThat(afterFirst.unclassifiedQualityHoldQuantity()).isEqualByComparingTo("0");
        assertThat(afterFirst.qualityBreakdownKnown()).isTrue();

        qualityService.decide(receipt.goodsReceiptId(), lineId,
                new PurchaseQcDecisionRequest(first.receiptVersion(), new BigDecimal("10"), BigDecimal.ZERO, null), null);

        StockBalanceAggregateResponse afterSecond = aggregateRow(f, item);
        assertThat(afterSecond.onHandQuantity()).isEqualByComparingTo("25");
        assertThat(afterSecond.qualityHoldQuantity()).isEqualByComparingTo("5");
        assertThat(afterSecond.availableQuantity()).isEqualByComparingTo("20");
        assertThat(afterSecond.pendingPurchaseQcQuantity()).isEqualByComparingTo("0");
        assertThat(afterSecond.rejectedPurchaseQcQuantity()).isEqualByComparingTo("5");
        assertThat(afterSecond.unclassifiedQualityHoldQuantity()).isEqualByComparingTo("0");
        assertThat(afterSecond.qualityBreakdownKnown()).isTrue();
    }

    private StockBalanceAggregateResponse aggregateRow(Fixture f, Item item) {
        List<StockBalanceAggregateResponse> rows = inventoryService.listAggregateBalances(f.warehouse().getWarehouseId(),
                item.getItemId(), null, PageRequest.of(0, 20)).content();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private UUID orderLineId(PurchaseOrder order) {
        return jdbc.queryForObject("select purchase_order_line_id from purchase_order_lines where purchase_order_id = ?",
                UUID.class, order.getPurchaseOrderId());
    }

    private static Map<String, PurchaseQcHoldProjection> byItemAndWarehouse(List<PurchaseQcHoldProjection> rows) {
        Map<String, PurchaseQcHoldProjection> map = new HashMap<>();
        rows.forEach(row -> assertThat(map.put(row.getItemId() + "@" + row.getWarehouseId(), row)).isNull());
        return map;
    }

    private void assertRefused(Item item, Fixture f, UUID lotId, String delta, String key) {
        assertThatThrownBy(() -> movementService.adjust(adjustCommand(item, f, lotId, delta), key,
                lotQcOwnershipGuard::ensureAdjustable))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    private static InventoryAdjustCommand adjustCommand(Item item, Fixture f, UUID lotId, String delta) {
        return new InventoryAdjustCommand(item.getItemId(), f.warehouse().getWarehouseId(), lotId, null,
                new BigDecimal(delta), "Correction", null, null, null, null);
    }

    private UUID lotId(Item item, String lotCode) {
        return jdbc.queryForObject("select lot_id from inventory_lots where item_id = ? and lot_code = ?",
                UUID.class, item.getItemId(), lotCode);
    }

    private BigDecimal lotQuantity(UUID lotId) {
        return jdbc.queryForObject("select quantity from stock_balances where lot_id = ?", BigDecimal.class, lotId);
    }

    private int movementCount(Item item) {
        return jdbc.queryForObject("select count(*) from stock_movements where item_id = ?", Integer.class, item.getItemId());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
