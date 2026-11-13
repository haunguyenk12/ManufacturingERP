package com.erp.manufacturing.module.delivery.service;

import com.erp.manufacturing.module.inventory.service.StockBalanceReservationService;
import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.delivery.domain.Delivery;
import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.domain.DeliveryStatus;
import com.erp.manufacturing.module.delivery.dto.DeliveryCancelRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryCreateRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryLineRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryPostRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryUpdateRequest;
import com.erp.manufacturing.module.delivery.repository.DeliveryLineRepository;
import com.erp.manufacturing.module.delivery.repository.DeliveryRepository;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.CommercialCommandResult;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import com.erp.manufacturing.module.sales.service.CommercialIdempotency;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * G10b deliveries on a real database (FE R05–R09, A05/A09/A10/A36). Only Postgres answers these: whether the two
 * {@code ISSUE} legs, the consumed holds and the posted delivery commit as one; whether the order lock serialises two
 * posts competing for the same free stock; and whether the quota is read from the stored lines. The wrapping
 * {@code @DataJpaTest} transaction is off — each service call commits on its own, as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({StockBalanceReservationService.class, DeliveryService.class, DeliverySupport.class, SalesOrderCommercialLookupService.class,
        SalesOrderStockReservationService.class, InventoryMovementService.class, ItemLookupService.class,
        OrganizationLookupService.class, CommercialIdempotency.class, IdempotencySupport.class, TraceIdProvider.class,
        com.erp.manufacturing.module.sales.service.SalesOrderPricingService.class, DeliveryPostingIT.Config.class})
class DeliveryPostingIT extends AbstractPostgresIntegrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-0000000010b0");

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class Config {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return () -> Optional.of(USER);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    @Autowired DeliveryService service;
    @Autowired DeliveryRepository deliveryRepository;
    @Autowired DeliveryLineRepository deliveryLineRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired MaterialReservationRepository reservationRepository;
    @Autowired StockBalanceRepository balanceRepository;
    @Autowired InventoryLotRepository lotRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.erp.manufacturing.module.sales.service.SalesOrderPricingService pricingService;

    @MockBean SecurityAuditorAware auditor;

    @BeforeEach
    void actor() {
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(USER));
    }

    /** FE Q14/A05: own holds first (one ISSUE), the rest from free stock (a second ISSUE), all in one commit. */
    @Test
    void post_takesTheOrdersOwnHoldFirstThenFreeStock_andCommitsEverythingTogether() {
        Fixture f = fixture("8");
        StockBalance balance = balance(f, "10");
        hold(f, balance, "3");

        CommercialCommandResult created = service.create(create(f, line(f, "5")), key("c"));
        Delivery draft = deliveryRepository.findById(created.documentId()).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo(DeliveryStatus.DRAFT);
        assertThat(draft.getCode()).matches("DN-\\d{6}");
        assertThat(onHand(f)).as("a draft moves no stock").isEqualByComparingTo("10");
        long orderVersion = salesOrderRepository.findById(f.order.getSalesOrderId()).orElseThrow().getVersion();

        String postKey = key("p");
        CommercialCommandResult posted = service.post(created.documentId(), new DeliveryPostRequest(created.version()), postKey);

        assertThat(posted.version()).isEqualTo(created.version() + 1);
        assertThat(deliveryRepository.findById(created.documentId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.POSTED);
        StockBalance after = balanceRepository.findById(balance.getBalanceId()).orElseThrow();
        assertThat(after.getQuantity()).isEqualByComparingTo("5");
        assertThat(after.getReservedQuantity()).as("the order's own hold is consumed").isEqualByComparingTo("0");
        MaterialReservation consumed = reservationRepository.findBySalesOrderIdAndStatus(
                f.order.getSalesOrderId(), MaterialReservationStatus.CONSUMED).get(0);
        assertThat(consumed.getConsumedQuantity()).isEqualByComparingTo("3");
        DeliveryLine line = deliveryLineRepository.findByDeliveryDeliveryIdOrderByLineNo(created.documentId()).get(0);
        assertThat(line.getReservedQuantity()).isEqualByComparingTo("3");
        assertThat(line.getReservedMovementId()).isNotNull();
        assertThat(line.getFreeMovementId()).isNotNull();
        assertThat(jdbc.queryForList("select quantity from stock_movements where reference_type = 'DELIVERY'"
                        + " and reference_id = ? order by quantity", BigDecimal.class, created.documentId().toString()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2"), new BigDecimal("3"));
        assertThat(salesOrderRepository.findById(f.order.getSalesOrderId()).orElseThrow().getVersion())
                .as("posting moves the order version by exactly one").isEqualTo(orderVersion + 1);

        // Replay of the same post: same answer, no second ISSUE.
        assertThat(service.post(created.documentId(), new DeliveryPostRequest(created.version()), postKey).version())
                .isEqualTo(posted.version());
        assertThat(onHand(f)).isEqualByComparingTo("5");
    }

    /** FE §3.5/A03/A07: the quota is ordered − D − other drafts; editing a draft never counts its own claim twice. */
    @Test
    void quota_excludesTheEditedDraft_andCountsPostedAndOtherDrafts() {
        Fixture f = fixture("10");
        balance(f, "20");
        CommercialCommandResult first = service.create(create(f, line(f, "3")), key("a"));
        service.post(first.documentId(), new DeliveryPostRequest(first.version()), key("ap"));
        CommercialCommandResult other = service.create(create(f, line(f, "5")), key("b"));

        CommercialCommandResult mine = service.create(create(f, line(f, "2")), key("c"));
        assertCode(() -> service.create(create(f, line(f, "0.000001")), key("d")), BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
        // 3 posted + 5 other draft ⇒ my draft may go 2 → 1 → 2, never 3
        CommercialCommandResult down = service.update(mine.documentId(), update(f, mine.version(), "1"), key("u1"));
        CommercialCommandResult up = service.update(mine.documentId(), update(f, down.version(), "2"), key("u2"));
        assertThat(up.version()).isEqualTo(down.version() + 1);
        assertCode(() -> service.update(mine.documentId(), update(f, up.version(), "3"), key("u3")),
                BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);

        // Cancelling the other draft returns its claim once.
        service.cancel(other.documentId(), new DeliveryCancelRequest(other.version(), "Customer split the order"), key("x"));
        service.update(mine.documentId(), update(f, up.version(), "7"), key("u4"));
        assertThat(deliveryLineRepository.findByDeliveryDeliveryIdOrderByLineNo(mine.documentId()))
                .extracting(DeliveryLine::getLineNo).containsExactly(1);
    }

    /**
     * Found by the G10 smoke, invisible to every other test: an edit that also changes the note was written twice —
     * once by the bulk delete's automatic flush, once by the final save — and the version moved by two (B212).
     */
    @Test
    void update_thatChangesTheNoteToo_movesTheVersionByExactlyOne() {
        Fixture f = fixture("10");
        balance(f, "10");
        CommercialCommandResult draft = service.create(create(f, line(f, "2")), key("n"));
        CommercialCommandResult edited = service.update(draft.documentId(), new DeliveryUpdateRequest(draft.version(),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), "another note", List.of(line(f, "3"))), key("n1"));
        assertThat(edited.version()).isEqualTo(draft.version() + 1);
        assertThat(jdbc.queryForObject("select version from deliveries where delivery_id = ?", Long.class,
                draft.documentId())).isEqualTo(edited.version());
    }

    /** Same trap on the price command: the price change dirties the header before the idempotency flush (B212). */
    @Test
    void setPrices_movesTheOrderVersionByExactlyOne() {
        Fixture f = fixture("10");
        SalesOrder order = salesOrderRepository.findById(f.order.getSalesOrderId()).orElseThrow();
        CommercialCommandResult result = pricingService.setPrices(order.getSalesOrderId(),
                new com.erp.manufacturing.module.sales.dto.SalesOrderCommercialPricesRequest(order.getVersion(), List.of(
                        new com.erp.manufacturing.module.sales.dto.SalesOrderLinePriceRequest(
                                f.order.getLines().get(0).getSalesOrderLineId(), new BigDecimal("100000")))),
                key("price"));
        assertThat(result.version()).isEqualTo(order.getVersion() + 1);
        assertThat(jdbc.queryForObject("select version from sales_orders where sales_order_id = ?", Long.class,
                order.getSalesOrderId())).isEqualTo(result.version());
        assertThat(jdbc.queryForObject("select count(*) from sales_order_price_events where sales_order_id = ?",
                Integer.class, order.getSalesOrderId())).isEqualTo(1);
    }

    /** FE A09: another order's hold on the same position is untouchable. */
    @Test
    void post_neverTakesAnotherOrdersHold() {
        Fixture f = fixture("10");
        StockBalance balance = balance(f, "6");
        SalesOrder other = order(f, "10");
        reservationRepository.save(MaterialReservation.builder().salesOrderId(other.getSalesOrderId())
                .salesOrderLineId(other.getLines().get(0).getSalesOrderLineId()).item(f.item).warehouse(f.warehouse)
                .lot(f.lot).quantity(new BigDecimal("4")).consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE).build());
        balance.reserve(new BigDecimal("4"));
        balanceRepository.save(balance);

        assertCode(() -> service.create(create(f, line(f, "3")), key("o")), BusinessErrorCode.INSUFFICIENT_STOCK);
        CommercialCommandResult two = service.create(create(f, line(f, "2")), key("o2"));
        service.post(two.documentId(), new DeliveryPostRequest(two.version()), key("o2p"));
        StockBalance after = balanceRepository.findById(balance.getBalanceId()).orElseThrow();
        assertThat(after.getQuantity()).isEqualByComparingTo("4");
        assertThat(after.getReservedQuantity()).isEqualByComparingTo("4");
    }

    /** FE R07/A08: a lot that expired after the draft was saved is refused at post; nothing moves. */
    @Test
    void post_ofALotThatExpiredSinceTheDraft_isRefusedAndMovesNothing() {
        Fixture f = fixture("5");
        balance(f, "5");
        CommercialCommandResult draft = service.create(create(f, line(f, "2")), key("e"));
        jdbc.update("update inventory_lots set expires_at = now() - interval '1 hour' where lot_id = ?", f.lot.getLotId());

        assertCode(() -> service.post(draft.documentId(), new DeliveryPostRequest(draft.version()), key("ep")),
                BusinessErrorCode.LOT_NOT_ELIGIBLE);
        assertThat(onHand(f)).isEqualByComparingTo("5");
        assertThat(deliveryRepository.findById(draft.documentId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.DRAFT);
    }

    /**
     * FE A36: two drafts (5 + 5, within the order's 10) race to post against 6 free. The order lock and the position
     * lock serialise them: one posts, the other sees 1 left and is refused — never a negative balance.
     */
    @Test
    void concurrentPosts_competingForTheSameFreeStock_oneWins_theOtherIsInsufficient() throws Exception {
        for (int round = 0; round < 3; round++) {
            Fixture f = fixture("10");
            balance(f, "6");
            CommercialCommandResult a = service.create(create(f, line(f, "5")), key("ra" + round));
            CommercialCommandResult b = service.create(create(f, line(f, "5")), key("rb" + round));

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (CommercialCommandResult draft : List.of(a, b)) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        service.post(draft.documentId(), new DeliveryPostRequest(draft.version()),
                                key("rp" + draft.documentId()));
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    try {
                        future.get(30, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        failures.add(e.getCause());
                    }
                }
            } finally {
                pool.shutdownNow();
            }
            assertThat(failures).as("round %d", round).singleElement()
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));
            assertThat(onHand(f)).as("round %d", round).isEqualByComparingTo("1");
        }
    }

    /** FE Q27/Q33: only the author edits or cancels a draft; a POSTED delivery cannot be cancelled or posted again. */
    @Test
    void lifecycle_onlyTheAuthorChangesADraft_andPostedIsFinal() {
        Fixture f = fixture("10");
        balance(f, "10");
        CommercialCommandResult draft = service.create(create(f, line(f, "2")), key("l"));
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.update(draft.documentId(), update(f, draft.version(), "1"), key("l1")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(com.erp.manufacturing.common.exception.AuthErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> service.cancel(draft.documentId(),
                new DeliveryCancelRequest(draft.version(), "Not mine to cancel"), key("l2")))
                .isInstanceOf(AppException.class);
        // POST authority may post someone else's draft (FE Q28).
        CommercialCommandResult posted = service.post(draft.documentId(), new DeliveryPostRequest(draft.version()), key("l3"));
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(USER));
        assertCode(() -> service.cancel(draft.documentId(), new DeliveryCancelRequest(posted.version(), "Too late now"),
                key("l4")), BusinessErrorCode.STATE_CONFLICT);
        assertCode(() -> service.post(draft.documentId(), new DeliveryPostRequest(posted.version()), key("l5")),
                BusinessErrorCode.STATE_CONFLICT);
        assertThat(deliveryRepository.findById(draft.documentId()).orElseThrow().getPostedBy()).isNotEqualTo(USER);
    }

    /**
     * Two orders, one stock position: the order locks do not serialise them, only the position lock does. Without it
     * the loser dies on the balance's {@code @Version} (a generic 409) instead of being told the stock is short.
     */
    @Test
    void concurrentPostsOfTwoOrders_onTheSamePosition_oneWins_theOtherIsInsufficient() throws Exception {
        for (int round = 0; round < 3; round++) {
            Fixture f = fixture("10");
            balance(f, "6");
            SalesOrder second = order(f, "10");
            CommercialCommandResult a = service.create(create(f, line(f, "5")), key("ta" + round));
            CommercialCommandResult b = service.create(new DeliveryCreateRequest(second.getSalesOrderId(),
                    salesOrderRepository.findById(second.getSalesOrderId()).orElseThrow().getVersion(),
                    LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), null, List.of(new DeliveryLineRequest(
                    second.getLines().get(0).getSalesOrderLineId(),
                    DeliverySupport.sourceKey(f.warehouse.getWarehouseId(), f.lot.getLotId()), new BigDecimal("5")))),
                    key("tb" + round));

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (CommercialCommandResult draft : List.of(a, b)) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        service.post(draft.documentId(), new DeliveryPostRequest(draft.version()),
                                key("tp" + draft.documentId()));
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    try {
                        future.get(30, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        failures.add(e.getCause());
                    }
                }
            } finally {
                pool.shutdownNow();
            }
            assertThat(failures).as("round %d", round).singleElement()
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));
            assertThat(onHand(f)).as("round %d", round).isEqualByComparingTo("1");
        }
    }

    @Test
    void database_refusesANonPositiveLineAndATakeFromHoldsAboveTheLine() {
        Fixture f = fixture("10");
        balance(f, "10");
        CommercialCommandResult draft = service.create(create(f, line(f, "2")), key("db"));
        UUID lineId = deliveryLineRepository.findByDeliveryDeliveryIdOrderByLineNo(draft.documentId()).get(0).getDeliveryLineId();
        assertThatThrownBy(() -> jdbc.update("update delivery_lines set quantity = 0 where delivery_line_id = ?", lineId))
                .hasMessageContaining("chk_delivery_lines_quantity");
        assertThatThrownBy(() -> jdbc.update("update delivery_lines set reserved_quantity = 3 where delivery_line_id = ?", lineId))
                .hasMessageContaining("chk_delivery_lines_reserved");
        assertThatThrownBy(() -> jdbc.update("update deliveries set status = 'POSTED' where delivery_id = ?", draft.documentId()))
                .hasMessageContaining("chk_deliveries_posted");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────────────────────

    private record Fixture(String suffix, Company company, Plant plant, Warehouse warehouse, Item item, InventoryLot lot,
                           Customer customer, SalesOrder order) {}

    private Fixture fixture(String ordered) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("DC_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("DP_" + suffix).name("Plant")
                .timezone("Asia/Ho_Chi_Minh").status(OrganizationStatus.ACTIVE).build());
        Warehouse warehouse = warehouseRepository.save(Warehouse.builder().plant(plant).code("DW_" + suffix)
                .name("FG").type(WarehouseType.FINISHED_GOODS).status(OrganizationStatus.ACTIVE).build());
        Item item = itemRepository.save(Item.builder().company(company).code("DI_" + suffix).name("Bike")
                .type(ItemType.FINISHED_GOOD).unit("PCS").lotTracked(true).status(ItemStatus.ACTIVE).build());
        InventoryLot lot = lotRepository.save(InventoryLot.builder().item(item).lotCode("DL-" + suffix)
                .status(LotStatus.AVAILABLE).receivedAt(java.time.Instant.parse("2026-10-01T00:00:00Z")).build());
        Customer customer = customerRepository.save(Customer.builder().company(company).code("CUS_" + suffix)
                .name("Customer " + suffix).status(CustomerStatus.ACTIVE).build());
        Fixture partial = new Fixture(suffix, company, plant, warehouse, item, lot, customer, null);
        return new Fixture(suffix, company, plant, warehouse, item, lot, customer, order(partial, ordered));
    }

    private SalesOrder order(Fixture f, String ordered) {
        SalesOrder order = SalesOrder.builder().company(f.company).plant(f.plant)
                .orderNo("SO-" + UUID.randomUUID().toString().substring(0, 8)).customerId(f.customer.getCustomerId())
                .customerName(f.customer.getName()).orderDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(3))
                .status(SalesOrderStatus.CONFIRMED).build();
        order.getLines().add(SalesOrderLine.builder().salesOrder(order).item(f.item).lineNo(1)
                .orderedQuantity(new BigDecimal(ordered)).dueDate(LocalDate.now().plusDays(10)).build());
        return salesOrderRepository.saveAndFlush(order);
    }

    private StockBalance balance(Fixture f, String quantity) {
        return balanceRepository.save(StockBalance.builder().item(f.item).warehouse(f.warehouse).lot(f.lot)
                .quantity(new BigDecimal(quantity)).build());
    }

    private void hold(Fixture f, StockBalance balance, String quantity) {
        reservationRepository.save(MaterialReservation.builder().salesOrderId(f.order.getSalesOrderId())
                .salesOrderLineId(f.order.getLines().get(0).getSalesOrderLineId()).item(f.item).warehouse(f.warehouse)
                .lot(f.lot).quantity(new BigDecimal(quantity)).consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE).build());
        balance.reserve(new BigDecimal(quantity));
        balanceRepository.save(balance);
    }

    private BigDecimal onHand(Fixture f) {
        return balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                f.item.getItemId(), f.warehouse.getWarehouseId(), f.lot.getLotId()).orElseThrow().getQuantity();
    }

    private DeliveryLineRequest line(Fixture f, String quantity) {
        return new DeliveryLineRequest(f.order.getLines().get(0).getSalesOrderLineId(),
                DeliverySupport.sourceKey(f.warehouse.getWarehouseId(), f.lot.getLotId()), new BigDecimal(quantity));
    }

    private DeliveryCreateRequest create(Fixture f, DeliveryLineRequest line) {
        long version = salesOrderRepository.findById(f.order.getSalesOrderId()).orElseThrow().getVersion();
        return new DeliveryCreateRequest(f.order.getSalesOrderId(), version,
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), "IT", List.of(line));
    }

    private DeliveryUpdateRequest update(Fixture f, Long version, String quantity) {
        return new DeliveryUpdateRequest(version, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), null,
                List.of(line(f, quantity)));
    }

    private static String key(String name) {
        return "IT-" + name + "-" + UUID.randomUUID();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, BusinessErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
