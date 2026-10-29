package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.service.InventoryCountCommand;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.InventoryService;
import com.erp.manufacturing.module.inventory.service.LotQcOwnershipGuard;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.workorder.service.query.LotQcOriginLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrder;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderLine;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import com.erp.manufacturing.module.purchasing.domain.Supplier;
import com.erp.manufacturing.module.purchasing.domain.SupplierStatus;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptDraftCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptDraftLineRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptResponse;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptVersionRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionResult;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.SupplierRepository;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CF-12: purchase QC ({@code releaseQualityHold}) and an absolute stock count race on the same non-tracked position.
 *
 * <p>The count locks the row ({@code lockPositionWithoutLot}) and compares the version the user read; the QC decision
 * locks the receipt and reads the balance <em>without</em> a row lock, relying on {@code @Version} of
 * {@code StockBalance}. Only a real database answers whether that is enough (rule R7): Hibernate writes every column on
 * update, so a QC that read the row before the count committed would put the old quantity back — a silent lost count —
 * if the version check did not refuse it.
 *
 * <p>Each interleaving is forced deterministically (latches, plus {@code pg_stat_activity} to see a statement blocked on
 * a row lock), then a free-running race repeats. After every round: whichever operation lost got a 409-family failure
 * ({@code CONCURRENT_MODIFICATION} or an optimistic-lock failure), never a wrong number — {@code reserved + hold <=
 * quantity}, the hold equals {@code received − accepted} of the receipt line, and quantity is 20 exactly when the count
 * committed.
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
        PurchaseQcCountRaceIT.Config.class})
class PurchaseQcCountRaceIT extends AbstractPostgresIntegrationTest {

    private static final String ZONE = "Asia/Ho_Chi_Minh";
    private static final int ROUNDS = 5;
    private static final long WAIT_SECONDS = 30;

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
    @Autowired PurchaseOrderRepository orderRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired StockBalanceRepository balanceRepository;
    @Autowired InventoryMovementService movementService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    /** One round: 25 received on hold, QC already accepted 10 — on hand 25, hold 15, available 10. */
    private record Round(UUID itemId, UUID warehouseId, UUID receiptId, UUID lineId, long receiptVersion,
                         long balanceVersion, String suffix) {}

    /**
     * The QC decision reads the balance, then the count commits, then the decision writes: the decision must be refused
     * (its UPDATE carries the version it read), not overwrite the counted quantity with the one it read.
     */
    @Test
    void qcReadsBeforeTheCountCommits_qcIsRefused_theCountedQuantityStays() throws Exception {
        Round r = round();
        CountDownLatch qcRead = new CountDownLatch(1);
        CountDownLatch countDone = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Object> qc = pool.submit(() -> outcome(() -> transactions.execute(status -> {
            balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(r.itemId(), r.warehouseId())
                    .orElseThrow().getQualityHoldQuantity();
            qcRead.countDown();
            await(countDone);
            return decide(r);
        })));
        Future<Object> count = pool.submit(() -> {
            await(qcRead);
            try {
                return outcome(() -> count(r));
            } finally {
                countDone.countDown();
            }
        });
        Object qcOutcome = qc.get(WAIT_SECONDS, TimeUnit.SECONDS);
        Object countOutcome = count.get(WAIT_SECONDS, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(countOutcome).isNotInstanceOf(Throwable.class);
        assertLostWith409(qcOutcome);
        assertConsistent(r, false, true);
    }

    /** The count holds the row lock (uncommitted) while the decision's UPDATE waits on it: the decision is refused. */
    @Test
    void countHoldsTheRowLockWhileQcWrites_qcIsRefused() throws Exception {
        Round r = round();
        CountDownLatch counted = new CountDownLatch(1);
        CountDownLatch qcBlocked = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Object> count = pool.submit(() -> outcome(() -> transactions.execute(status -> {
            Object result = count(r);
            counted.countDown();
            await(qcBlocked);
            return result;
        })));
        Future<Object> qc = pool.submit(() -> {
            await(counted);
            return outcome(() -> decide(r));
        });
        awaitStatementBlockedOnStockBalances();
        qcBlocked.countDown();
        Object countOutcome = count.get(WAIT_SECONDS, TimeUnit.SECONDS);
        Object qcOutcome = qc.get(WAIT_SECONDS, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(countOutcome).isNotInstanceOf(Throwable.class);
        assertLostWith409(qcOutcome);
        assertConsistent(r, false, true);
    }

    /** The decision has written (uncommitted) when the count asks for the row lock: the count sees the new version. */
    @Test
    void qcHoldsTheRowWhileTheCountLocks_countIsRefusedOnItsVersion() throws Exception {
        Round r = round();
        CountDownLatch decided = new CountDownLatch(1);
        CountDownLatch countBlocked = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Object> qc = pool.submit(() -> outcome(() -> transactions.execute(status -> {
            Object result = decide(r);
            decided.countDown();
            await(countBlocked);
            return result;
        })));
        Future<Object> count = pool.submit(() -> {
            await(decided);
            return outcome(() -> count(r));
        });
        awaitStatementBlockedOnStockBalances();
        countBlocked.countDown();
        Object qcOutcome = qc.get(WAIT_SECONDS, TimeUnit.SECONDS);
        Object countOutcome = count.get(WAIT_SECONDS, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(qcOutcome).isNotInstanceOf(Throwable.class);
        assertThat(countOutcome).isInstanceOf(AppException.class);
        assertThat(((AppException) countOutcome).getErrorCode()).isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION);
        assertConsistent(r, true, false);
    }

    /** Control: the count commits first and the decision reads afterwards — both are legitimate and both apply. */
    @Test
    void countThenQc_inSequence_bothApply() {
        Round r = round();
        count(r);
        decide(r);
        assertConsistent(r, true, true);
    }

    /** Free-running race, several rounds: one or both commit, a loser always gets a 409, the numbers always add up. */
    @Test
    void freeRace_neverLeavesAWrongNumber() throws Exception {
        for (int i = 0; i < ROUNDS; i++) {
            Round r = round();
            CyclicBarrier start = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<Object> qc = pool.submit(() -> {
                start.await(WAIT_SECONDS, TimeUnit.SECONDS);
                return outcome(() -> decide(r));
            });
            Future<Object> count = pool.submit(() -> {
                start.await(WAIT_SECONDS, TimeUnit.SECONDS);
                return outcome(() -> count(r));
            });
            Object qcOutcome = qc.get(WAIT_SECONDS, TimeUnit.SECONDS);
            Object countOutcome = count.get(WAIT_SECONDS, TimeUnit.SECONDS);
            pool.shutdown();

            boolean qcApplied = !(qcOutcome instanceof Throwable);
            boolean countApplied = !(countOutcome instanceof Throwable);
            assertThat(qcApplied || countApplied).as("round %d: at least one commits", i).isTrue();
            if (!qcApplied) {
                assertLostWith409(qcOutcome);
            }
            if (!countApplied) {
                assertLostWith409(countOutcome);
            }
            assertConsistent(r, qcApplied, countApplied);
        }
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────────────────

    private Round round() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("QCR_" + suffix).name("Co")
                .status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("P_" + suffix).name("Plant").timezone(ZONE)
                .status(OrganizationStatus.ACTIVE).build());
        Warehouse warehouse = warehouseRepository.save(Warehouse.builder().plant(plant).code("W_" + suffix).name("Wh")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build());
        Supplier supplier = supplierRepository.save(Supplier.builder().company(company).code("S_" + suffix).name("Acme")
                .status(SupplierStatus.ACTIVE).build());
        Item item = itemRepository.save(Item.builder().company(company).code("I_" + suffix).name("Raw")
                .type(ItemType.RAW_MATERIAL).unit("KG").lotTracked(false).status(ItemStatus.ACTIVE).build());

        LocalDate today = LocalDate.now(ZoneId.of(ZONE));
        PurchaseOrder order = PurchaseOrder.builder().company(company).plant(plant).warehouse(warehouse)
                .supplier(supplier).purchaseOrderNo("PO-" + suffix).status(PurchaseOrderStatus.CONFIRMED)
                .orderDate(today.minusDays(2)).expectedDate(today).build();
        order.getLines().add(PurchaseOrderLine.builder().purchaseOrder(order).item(item)
                .orderedQuantity(new BigDecimal("40")).receivedQuantity(BigDecimal.ZERO)
                .unitPrice(new BigDecimal("10")).currencyCode("VND").expectedDate(today).build());
        order = orderRepository.saveAndFlush(order);
        UUID orderLineId = jdbc.queryForObject(
                "select purchase_order_line_id from purchase_order_lines where purchase_order_id = ?",
                UUID.class, order.getPurchaseOrderId());
        GoodsReceiptResponse draft = receiptService.createDraft(new GoodsReceiptDraftCreateRequest(order.getPurchaseOrderId(),
                today, null, null, List.of(new GoodsReceiptDraftLineRequest(null, orderLineId, new BigDecimal("25"),
                null, null))), null);
        GoodsReceiptResponse receipt = receiptService.post(draft.goodsReceiptId(),
                new GoodsReceiptVersionRequest(draft.version()), null);
        UUID lineId = receipt.lines().get(0).goodsReceiptLineId();
        PurchaseQcDecisionResult first = qualityService.decide(receipt.goodsReceiptId(), lineId,
                new PurchaseQcDecisionRequest(receipt.version(), new BigDecimal("10"), BigDecimal.ZERO, null), null);
        long balanceVersion = jdbc.queryForObject(
                "select version from stock_balances where item_id = ? and lot_id is null", Long.class, item.getItemId());
        Round round = new Round(item.getItemId(), warehouse.getWarehouseId(), receipt.goodsReceiptId(), lineId,
                first.receiptVersion(), balanceVersion, suffix);
        assertBalance(round, "25", "15");
        return round;
    }

    /** QC accepts 5 more of the 15 pending. */
    private PurchaseQcDecisionResult decide(Round r) {
        return qualityService.decide(r.receiptId(), r.lineId(),
                new PurchaseQcDecisionRequest(r.receiptVersion(), new BigDecimal("5"), BigDecimal.ZERO, null), null);
    }

    /** Counts 20 — above the hold whether or not the decision has released its 5. */
    private Object count(Round r) {
        return movementService.count(new InventoryCountCommand(r.itemId(), r.warehouseId(), null, new BigDecimal("20"),
                r.balanceVersion(), "Cycle count"), "CF12-RACE-" + r.suffix());
    }

    // ── assertions ──────────────────────────────────────────────────────────────────────────────

    private void assertConsistent(Round r, boolean qcApplied, boolean countApplied) {
        Map<String, Object> balance = jdbc.queryForMap("""
                select quantity, reserved_quantity, quality_hold_quantity from stock_balances
                where item_id = ? and lot_id is null
                """, r.itemId());
        BigDecimal quantity = (BigDecimal) balance.get("quantity");
        BigDecimal reserved = (BigDecimal) balance.get("reserved_quantity");
        BigDecimal hold = (BigDecimal) balance.get("quality_hold_quantity");
        Map<String, Object> line = jdbc.queryForMap(
                "select received_quantity, accepted_quantity from goods_receipt_lines where goods_receipt_line_id = ?",
                r.lineId());
        BigDecimal received = (BigDecimal) line.get("received_quantity");
        BigDecimal accepted = (BigDecimal) line.get("accepted_quantity");

        assertThat(reserved.add(hold)).isLessThanOrEqualTo(quantity);
        assertThat(hold).as("hold = received − accepted of the line").isEqualByComparingTo(received.subtract(accepted));
        assertThat(accepted).isEqualByComparingTo(qcApplied ? "15" : "10");
        assertThat(hold).isEqualByComparingTo(qcApplied ? "10" : "15");
        assertThat(quantity).isEqualByComparingTo(countApplied ? "20" : "25");
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where item_id = ? and movement_type = 'ADJUST_OUT'",
                Integer.class, r.itemId())).isEqualTo(countApplied ? 1 : 0);
        assertThat(jdbc.queryForObject("select count(*) from purchase_quality_decisions where goods_receipt_line_id = ?",
                Integer.class, r.lineId())).isEqualTo(qcApplied ? 2 : 1);
    }

    private void assertBalance(Round r, String quantity, String hold) {
        Map<String, Object> balance = jdbc.queryForMap(
                "select quantity, quality_hold_quantity from stock_balances where item_id = ? and lot_id is null", r.itemId());
        assertThat((BigDecimal) balance.get("quantity")).isEqualByComparingTo(quantity);
        assertThat((BigDecimal) balance.get("quality_hold_quantity")).isEqualByComparingTo(hold);
    }

    /** A loser is a 409: the count's own version check, or the decision's stale {@code @Version} (409 over HTTP). */
    private static void assertLostWith409(Object outcome) {
        assertThat(outcome).isInstanceOf(Throwable.class);
        if (outcome instanceof AppException app) {
            assertThat(app.getErrorCode()).isIn(BusinessErrorCode.CONCURRENT_MODIFICATION, BusinessErrorCode.STATE_CONFLICT);
        } else {
            assertThat(outcome).isInstanceOf(OptimisticLockingFailureException.class);
        }
    }

    /** Bounded poll: some statement on stock_balances is waiting on a row lock. */
    private void awaitStatementBlockedOnStockBalances() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock' and query ilike '%stock_balances%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no statement on stock_balances blocked on a row lock within " + WAIT_SECONDS + "s");
    }

    private interface Action {
        Object run();
    }

    private static Object outcome(Action action) {
        try {
            return action.run();
        } catch (RuntimeException ex) {
            return ex;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
