package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.SupplierRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Goods receipt drafts and posting on a real database (G7b). Only Postgres answers what is checked
 * here (rule R7): the purchase order row lock that serialises two concurrent posts against the same
 * open quantity (D11), what posting on hold really writes to {@code stock_balances} (D4), and the
 * plant-wide receipt / candidate queries with every optional filter left out (§0.24).
 *
 * <p>The {@code @DataJpaTest} wrapping transaction is off: each service call commits on its own, as
 * in production. {@code @PreAuthorize} is not active in this slice — authorisation has its own tests.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({GoodsReceiptDraftService.class, GoodsReceiptPoster.class, PurchasingIdempotency.class, PurchasingMapper.class,
        IdempotencySupport.class, TraceIdProvider.class, InventoryMovementService.class, ItemLookupService.class,
        OrganizationLookupService.class, GoodsReceiptPostingIT.Config.class})
class GoodsReceiptPostingIT extends AbstractPostgresIntegrationTest {

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

    @Autowired GoodsReceiptDraftService service;
    @Autowired GoodsReceiptRepository receiptRepository;
    @Autowired PurchaseOrderRepository orderRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;

    private record Fixture(Company company, Plant plant, Warehouse warehouse, Supplier supplier, String suffix) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("GR_" + suffix).name("Co").status(OrganizationStatus.ACTIVE).build());
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

    private PurchaseOrder order(Fixture f, Item item, String ordered, PurchaseOrderStatus status) {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));
        PurchaseOrder order = PurchaseOrder.builder().company(f.company()).plant(f.plant()).warehouse(f.warehouse())
                .supplier(f.supplier()).purchaseOrderNo("PO-" + UUID.randomUUID().toString().substring(0, 8))
                .status(status).orderDate(today.minusDays(2)).expectedDate(today).build();
        order.getLines().add(PurchaseOrderLine.builder().purchaseOrder(order).item(item)
                .orderedQuantity(new BigDecimal(ordered)).receivedQuantity(BigDecimal.ZERO)
                .unitPrice(new BigDecimal("10")).currencyCode("VND").expectedDate(today).build());
        return orderRepository.saveAndFlush(order);
    }

    private UUID lineId(PurchaseOrder order) {
        return jdbc.queryForObject("select purchase_order_line_id from purchase_order_lines where purchase_order_id = ?",
                UUID.class, order.getPurchaseOrderId());
    }

    private GoodsReceiptResponse draft(PurchaseOrder order, String quantity, String lotCode, LocalDate expiresOn) {
        return service.createDraft(new GoodsReceiptDraftCreateRequest(order.getPurchaseOrderId(),
                LocalDate.now(ZoneId.of(ZONE)), null, null,
                List.of(new GoodsReceiptDraftLineRequest(null, lineId(order), new BigDecimal(quantity), lotCode, expiresOn))), null);
    }

    @Test
    void draftMovesNothing_postReceivesOnHold_withoutALot() {
        Fixture f = fixture();
        Item item = item(f, false);
        PurchaseOrder order = order(f, item, "40", PurchaseOrderStatus.CONFIRMED);

        GoodsReceiptResponse draft = draft(order, "25", null, null);

        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where item_id = ?", Integer.class, item.getItemId()))
                .isZero();

        GoodsReceiptResponse posted = service.post(draft.goodsReceiptId(), new GoodsReceiptVersionRequest(draft.version()), null);

        assertThat(posted.status()).isEqualTo("POSTED");
        Map<String, Object> balance = jdbc.queryForMap(
                "select quantity, quality_hold_quantity, reserved_quantity from stock_balances where item_id = ?", item.getItemId());
        assertThat((BigDecimal) balance.get("quantity")).isEqualByComparingTo("25");
        assertThat((BigDecimal) balance.get("quality_hold_quantity")).isEqualByComparingTo("25");
        assertThat(jdbc.queryForObject("select received_quantity from purchase_order_lines where purchase_order_id = ?",
                BigDecimal.class, order.getPurchaseOrderId())).isEqualByComparingTo("25");
        assertThat(jdbc.queryForObject("select status from purchase_orders where purchase_order_id = ?",
                String.class, order.getPurchaseOrderId())).isEqualTo("PARTIALLY_RECEIVED");
        GoodsReceipt reloaded = receiptRepository.findById(posted.goodsReceiptId()).orElseThrow();
        assertThat(reloaded.isQualityHeld()).isTrue();
        // Response and re-read agree to the microsecond (CLAUDE.md §0.51 #3).
        assertThat(reloaded.getPostedAt()).isEqualTo(posted.postedAt());
    }

    @Test
    void postLotTracked_createsTheLotOnHold_withThePlantLocalExpiry() {
        Fixture f = fixture();
        Item item = item(f, true);
        PurchaseOrder order = order(f, item, "10", PurchaseOrderStatus.CONFIRMED);
        LocalDate expiresOn = LocalDate.now(ZoneId.of(ZONE)).plusDays(60);
        GoodsReceiptResponse draft = draft(order, "4", "LOT-" + f.suffix(), expiresOn);

        GoodsReceiptResponse posted = service.post(draft.goodsReceiptId(), new GoodsReceiptVersionRequest(draft.version()), null);

        Map<String, Object> lot = jdbc.queryForMap("select status, expires_at from inventory_lots where item_id = ?", item.getItemId());
        assertThat(lot.get("status")).isEqualTo("HOLD");
        assertThat(((java.sql.Timestamp) lot.get("expires_at")).toInstant())
                .isEqualTo(expiresOn.atStartOfDay(ZoneId.of(ZONE)).toInstant());
        assertThat(posted.lines().get(0).lotStatus()).isEqualTo("HOLD");
        assertThat(jdbc.queryForObject("select quality_hold_quantity from stock_balances where item_id = ?",
                BigDecimal.class, item.getItemId())).isEqualByComparingTo("0");
    }

    /**
     * D11 / FE A10: two drafts of 10 against 15 open, posted at the same time. The order row lock
     * makes the second post read the first one's received quantity, so it fails as an over-receipt
     * naming its line — not as an optimistic-lock or constraint error, and never receiving 20.
     */
    @Test
    void twoConcurrentPosts_againstTheSameOpenQuantity_exactlyOneWins_theOtherIsAnOverReceipt() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Fixture f = fixture();
            Item item = item(f, false);
            PurchaseOrder order = order(f, item, "15", PurchaseOrderStatus.CONFIRMED);
            GoodsReceiptResponse first = draft(order, "10", null, null);
            GoodsReceiptResponse second = draft(order, "10", null, null);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> results = new ArrayList<>();
            for (GoodsReceiptResponse draft : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        return service.post(draft.goodsReceiptId(), new GoodsReceiptVersionRequest(draft.version()), null);
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();

            assertThat(outcomes).filteredOn(GoodsReceiptResponse.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(Exception.class::isInstance).singleElement().satisfies(ex -> {
                assertThat(ex).isInstanceOf(AppException.class);
                assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
            });
            assertThat(jdbc.queryForObject("select received_quantity from purchase_order_lines where purchase_order_id = ?",
                    BigDecimal.class, order.getPurchaseOrderId())).isEqualByComparingTo("10");
            assertThat(jdbc.queryForObject("select quantity from stock_balances where item_id = ?",
                    BigDecimal.class, item.getItemId())).isEqualByComparingTo("10");
        }
    }

    /**
     * Production runs with open-session-in-view: the {@code hasReceiptAccess} guard loads the receipt
     * graph — purchase order and its lines included — into the request's EntityManager <b>before</b> the
     * service transaction starts, and that transaction then reuses it. The row lock must still see the
     * committed open quantity, so the loser is an over-receipt, not a stale-version 409 (smoke G7b B11).
     */
    @Test
    void concurrentPosts_whenTheGuardAlreadyLoadedTheOrder_theLoserStillSeesTheCommittedOpenQuantity() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Fixture f = fixture();
            Item item = item(f, false);
            PurchaseOrder order = order(f, item, "15", PurchaseOrderStatus.CONFIRMED);
            GoodsReceiptResponse first = draft(order, "10", null, null);
            GoodsReceiptResponse second = draft(order, "10", null, null);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CyclicBarrier bothLoaded = new CyclicBarrier(2);
            List<Future<Object>> results = new ArrayList<>();
            for (GoodsReceiptResponse draft : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    try {
                        return transactions.execute(status -> {
                            // Exactly what the guard loads: the receipt graph, whose lines reach the purchase order
                            // lines directly — the order's own lines collection stays uninitialised.
                            receiptRepository.findWithDetailsByGoodsReceiptId(draft.goodsReceiptId())
                                    .ifPresent(r -> r.getLines().forEach(l -> l.getPurchaseOrderLine().getReceivedQuantity()));
                            await(bothLoaded);
                            return service.post(draft.goodsReceiptId(), new GoodsReceiptVersionRequest(draft.version()), null);
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

            assertThat(outcomes).filteredOn(GoodsReceiptResponse.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(Exception.class::isInstance).singleElement().satisfies(ex -> {
                assertThat(ex).isInstanceOf(AppException.class);
                assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
            });
            assertThat(jdbc.queryForObject("select received_quantity from purchase_order_lines where purchase_order_id = ?",
                    BigDecimal.class, order.getPurchaseOrderId())).isEqualByComparingTo("10");
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void plantList_andCandidates_workWithEveryOptionalFilterLeftOut() {
        Fixture f = fixture();
        Item item = item(f, false);
        PurchaseOrder open = order(f, item, "5", PurchaseOrderStatus.CONFIRMED);
        PurchaseOrder draftOrder = order(f, item, "5", PurchaseOrderStatus.DRAFT);
        GoodsReceiptResponse receipt = draft(open, "2", null, null);

        var all = service.list(f.company().getCompanyId(), f.plant().getPlantId(), null, null, null, null, null, null,
                PageRequest.of(0, 20));
        assertThat(all.content()).extracting(GoodsReceiptResponse::goodsReceiptId).containsExactly(receipt.goodsReceiptId());
        var bySupplier = service.list(f.company().getCompanyId(), f.plant().getPlantId(), null, null,
                GoodsReceiptStatus.DRAFT, "acme met", LocalDate.now(ZoneId.of(ZONE)), LocalDate.now(ZoneId.of(ZONE)),
                PageRequest.of(0, 20));
        assertThat(bySupplier.totalElements()).isEqualTo(1);

        var candidates = service.candidates(f.company().getCompanyId(), f.plant().getPlantId(), null, PageRequest.of(0, 20));
        assertThat(candidates.content()).extracting(GoodsReceiptCandidateResponse::purchaseOrderId)
                .containsExactly(open.getPurchaseOrderId())
                .doesNotContain(draftOrder.getPurchaseOrderId());
    }
}
