package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceLine;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCancelRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCreateRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceLineRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePostRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceUpdateRequest;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceLineRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.delivery.domain.Delivery;
import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.repository.DeliveryLineRepository;
import com.erp.manufacturing.module.delivery.repository.DeliveryRepository;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * G10c invoices on a real database (FE R11–R15, A14–A23). Only Postgres answers these: whether the quota and the
 * billing base are read from the stored lines of other invoices; whether the order lock serialises two drafts competing
 * for the same delivered quantity; and whether a post refuses a base that moved since the save. The wrapping
 * {@code @DataJpaTest} transaction is off — each service call commits on its own, as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SalesInvoiceService.class, SalesInvoiceSupport.class, SalesOrderCommercialLookupService.class,
        DeliveryLookupService.class, CommercialIdempotency.class, IdempotencySupport.class, SalesInvoiceIT.Config.class})
class SalesInvoiceIT extends AbstractPostgresIntegrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-0000000010c0");
    private static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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

    @Autowired SalesInvoiceService service;
    @Autowired SalesInvoiceRepository invoiceRepository;
    @Autowired SalesInvoiceLineRepository invoiceLineRepository;
    @Autowired DeliveryRepository deliveryRepository;
    @Autowired DeliveryLineRepository deliveryLineRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired JdbcTemplate jdbc;

    @MockBean SecurityAuditorAware auditor;

    @BeforeEach
    void actor() {
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(USER));
    }

    /** FE A14/A17: 3 + 1 + 4 at 100 000 is 800 000; a draft and a post move no stock and never the order's version. */
    @Test
    void previewSaveAndPost_billTheDeliveredLinesAtTheLockedPrice_andMoveNoStock() {
        Fixture f = fixture("10", "100000");
        List<DeliveryLine> first = postedDelivery(f, "3", "1");
        List<DeliveryLine> second = postedDelivery(f, "4");
        long movements = countMovements();
        long orderVersion = orderVersion(f);
        List<SalesInvoiceLineRequest> lines = List.of(line(first.get(0), "3"), line(first.get(1), "1"), line(second.get(0), "4"));

        SalesInvoicePreviewResponse preview = service.preview(preview(f, lines));
        assertThat(preview.amount()).isEqualByComparingTo("800000");
        assertThat(preview.lines()).extracting(l -> l.amount().toPlainString()).containsExactly("300000", "100000", "400000");

        CommercialCommandResult created = service.create(create(f, preview.billingSnapshotVersion(), lines), key("c"));
        SalesInvoice draft = invoiceRepository.findById(created.documentId()).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo(SalesInvoiceStatus.DRAFT);
        assertThat(draft.getCode()).matches("INV-\\d{6}");
        assertThat(draft.getAmount()).isEqualByComparingTo("800000");

        String postKey = key("p");
        CommercialCommandResult posted = service.post(created.documentId(), new SalesInvoicePostRequest(created.version()), postKey);
        assertThat(posted.version()).isEqualTo(created.version() + 1);
        assertThat(invoiceRepository.findById(created.documentId()).orElseThrow().getStatus()).isEqualTo(SalesInvoiceStatus.POSTED);
        assertThat(countMovements()).as("an invoice never touches stock").isEqualTo(movements);
        assertThat(orderVersion(f)).as("an invoice changes no field of the order").isEqualTo(orderVersion);

        // Replay of the same post: same answer, still posted once.
        assertThat(service.post(created.documentId(), new SalesInvoicePostRequest(created.version()), postKey).version())
                .isEqualTo(posted.version());
    }

    /** FE A21: three invoices of a third at 1 dong bill 0, 1, 0 — each priced on the base the previous one posted. */
    @Test
    void partialInvoicesOfAThird_billZeroOneZero_andAddUpToTheWhole() {
        Fixture f = fixture("1", "1");
        DeliveryLine delivered = postedDelivery(f, "1").get(0);
        List<String> amounts = new ArrayList<>();
        for (String quantity : List.of("0.333333", "0.333333", "0.333334")) {
            List<SalesInvoiceLineRequest> lines = List.of(line(delivered, quantity));
            SalesInvoicePreviewResponse preview = service.preview(preview(f, lines));
            CommercialCommandResult created = service.create(create(f, preview.billingSnapshotVersion(), lines), key("c"));
            service.post(created.documentId(), new SalesInvoicePostRequest(created.version()), key("p"));
            amounts.add(invoiceRepository.findById(created.documentId()).orElseThrow().getAmount().toPlainString());
        }
        assertThat(amounts).containsExactly("0", "1", "0");
    }

    /**
     * FE A22: two drafts saved on the same base; once the first posts, the second's base has moved — its post is
     * refused and nothing changes, until it is previewed and saved again on the new base.
     */
    @Test
    void twoDraftsOnTheSameBase_theSecondPostIsPriceStale_untilPreviewedAndSavedAgain() {
        Fixture f = fixture("1", "1");
        DeliveryLine delivered = postedDelivery(f, "1").get(0);
        List<SalesInvoiceLineRequest> half = List.of(line(delivered, "0.5"));
        String base = service.preview(preview(f, half)).billingSnapshotVersion();
        CommercialCommandResult one = service.create(create(f, base, half), key("1"));
        CommercialCommandResult two = service.create(create(f, base, half), key("2"));
        assertThat(invoiceRepository.findById(two.documentId()).orElseThrow().getAmount())
                .as("both priced as the first half: round(0.5) = 1").isEqualByComparingTo("1");

        service.post(one.documentId(), new SalesInvoicePostRequest(one.version()), key("p1"));
        assertCode(() -> service.post(two.documentId(), new SalesInvoicePostRequest(two.version()), key("p2")),
                BusinessErrorCode.PRICE_STALE);
        assertThat(invoiceRepository.findById(two.documentId()).orElseThrow().getStatus()).isEqualTo(SalesInvoiceStatus.DRAFT);

        String newBase = service.preview(new SalesInvoicePreviewRequest(f.order.getSalesOrderId(), orderVersion(f),
                two.documentId(), half)).billingSnapshotVersion();
        assertThat(newBase).isNotEqualTo(base);
        assertCode(() -> service.update(two.documentId(), update(two.version(), base, half), key("u-old")),
                BusinessErrorCode.PRICE_STALE);
        CommercialCommandResult resaved = service.update(two.documentId(), update(two.version(), newBase, half), key("u"));
        assertThat(invoiceRepository.findById(two.documentId()).orElseThrow().getAmount())
                .as("the second half on a base of 1 dong: round(1.0) − 1 = 0").isEqualByComparingTo("0");
        service.post(two.documentId(), new SalesInvoicePostRequest(resaved.version()), key("p3"));
    }

    /**
     * FE R14/Q22: a moved base is stale even when the amounts happen not to change (whole quantities at a whole price) —
     * otherwise the posted lines would carry a wrong {@code billedQuantityBefore}/{@code billedAmountBefore}.
     */
    @Test
    void aMovedBaseIsStale_evenWhenTheAmountsWouldNotChange() {
        Fixture f = fixture("10", "100000");
        DeliveryLine delivered = postedDelivery(f, "2").get(0);
        List<SalesInvoiceLineRequest> one = List.of(line(delivered, "1"));
        String base = service.preview(preview(f, one)).billingSnapshotVersion();
        CommercialCommandResult first = service.create(create(f, base, one), key("1"));
        CommercialCommandResult second = service.create(create(f, base, one), key("2"));
        service.post(first.documentId(), new SalesInvoicePostRequest(first.version()), key("p1"));

        assertCode(() -> service.post(second.documentId(), new SalesInvoicePostRequest(second.version()), key("p2")),
                BusinessErrorCode.PRICE_STALE);
        SalesInvoiceLine stored = invoiceLineRepository.findByInvoiceInvoiceIdOrderByLineNo(second.documentId()).get(0);
        assertThat(stored.getAmount()).as("the amount itself is still right").isEqualByComparingTo("100000");
        assertThat(stored.getBilledQuantityBefore()).as("but the base it carries is not").isEqualByComparingTo("0");
    }

    /** FE A19, A23: a missing price is never billed as 0; a stored amount that is not the server's is refused at post. */
    @Test
    void missingPriceIsPriceUnknown_staleSnapshotIsRefused_andATamperedAmountNeverPosts() {
        Fixture unpriced = fixture("5", null);
        DeliveryLine noPrice = postedDelivery(unpriced, "2").get(0);
        assertCode(() -> service.preview(preview(unpriced, List.of(line(noPrice, "2")))), BusinessErrorCode.PRICE_UNKNOWN);

        Fixture f = fixture("5", "1000");
        DeliveryLine delivered = postedDelivery(f, "2").get(0);
        List<SalesInvoiceLineRequest> lines = List.of(line(delivered, "2"));
        long invoices = invoiceRepository.count();
        assertCode(() -> service.create(create(f, "BILL-0000000000000000", lines), key("s")), BusinessErrorCode.PRICE_STALE);
        assertThat(invoiceRepository.count()).as("nothing saved").isEqualTo(invoices);

        String base = service.preview(preview(f, lines)).billingSnapshotVersion();
        CommercialCommandResult created = service.create(create(f, base, lines), key("c"));
        UUID lineId = invoiceLineRepository.findByInvoiceInvoiceIdOrderByLineNo(created.documentId()).get(0).getInvoiceLineId();
        jdbc.update("update sales_invoice_lines set amount = 1 where invoice_line_id = ?", lineId);
        assertCode(() -> service.post(created.documentId(), new SalesInvoicePostRequest(created.version()), key("p")),
                BusinessErrorCode.PRICE_STALE);
        assertThat(invoiceRepository.findById(created.documentId()).orElseThrow().getStatus()).isEqualTo(SalesInvoiceStatus.DRAFT);
    }

    /** FE A15/A16: delivered − posted − other drafts; editing excludes the draft's own claim; cancel returns it once. */
    @Test
    void quota_isDeliveredMinusPostedMinusOtherDrafts_andCancelReturnsTheClaim() {
        Fixture f = fixture("10", "10");
        DeliveryLine delivered = postedDelivery(f, "5").get(0);
        CommercialCommandResult billed = createPriced(f, delivered, "2");
        service.post(billed.documentId(), new SalesInvoicePostRequest(billed.version()), key("p"));
        CommercialCommandResult other = createPriced(f, delivered, "1");

        assertCode(() -> createPriced(f, delivered, "3"), BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
        CommercialCommandResult mine = createPriced(f, delivered, "2");
        List<SalesInvoiceLineRequest> two = List.of(line(delivered, "2"));
        String base = service.preview(new SalesInvoicePreviewRequest(f.order.getSalesOrderId(), orderVersion(f),
                mine.documentId(), two)).billingSnapshotVersion();
        CommercialCommandResult edited = service.update(mine.documentId(), update(mine.version(), base, two), key("e"));
        assertThat(edited.version()).as("an edit moves the version by exactly one").isEqualTo(mine.version() + 1);
        assertCode(() -> service.update(mine.documentId(), update(edited.version(), base,
                List.of(line(delivered, "3"))), key("e3")), BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);

        CommercialCommandResult cancelled = service.cancel(other.documentId(),
                new SalesInvoiceCancelRequest(other.version(), "customer asked to wait"), key("x"));
        assertThat(invoiceRepository.findById(other.documentId()).orElseThrow().getStatus()).isEqualTo(SalesInvoiceStatus.CANCELLED);
        assertThat(cancelled.version()).isEqualTo(other.version() + 1);
        assertThat(createPriced(f, delivered, "1")).as("the cancelled claim is free again").isNotNull();
        assertCode(() -> service.cancel(other.documentId(),
                new SalesInvoiceCancelRequest(cancelled.version(), "once more please"), key("x2")), BusinessErrorCode.STATE_CONFLICT);
    }

    /** FE Q27/R11: only the author edits or cancels; sources are POSTED delivery lines of the same order only. */
    @Test
    void onlyTheAuthorChangesADraft_andSourcesArePostedLinesOfThisOrder() {
        Fixture f = fixture("10", "10");
        DeliveryLine delivered = postedDelivery(f, "5").get(0);
        CommercialCommandResult draft = createPriced(f, delivered, "1");

        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        assertCode(() -> service.cancel(draft.documentId(), new SalesInvoiceCancelRequest(draft.version(), "not mine"),
                key("o")), AuthErrorCode.ACCESS_DENIED);
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(USER));

        // FE R27: not after the plant's today, not before the source delivery (yesterday), due on or after the date.
        LocalDate today = LocalDate.now(PLANT_ZONE);
        List<SalesInvoiceLineRequest> one = List.of(line(delivered, "1"));
        String base = service.preview(preview(f, one)).billingSnapshotVersion();
        for (LocalDate[] dates : List.of(new LocalDate[]{today.minusDays(2), today}, new LocalDate[]{today.plusDays(1), today.plusDays(2)},
                new LocalDate[]{today, today.minusDays(1)})) {
            assertCode(() -> service.create(new SalesInvoiceCreateRequest(f.order.getSalesOrderId(), orderVersion(f), base,
                    dates[0], dates[1], null, one), key("d")), ValidationErrorCode.INVALID_INPUT);
        }

        DeliveryLine draftDelivery = delivery(f, false, "2").get(0);
        assertCode(() -> service.preview(preview(f, List.of(line(draftDelivery, "1")))), BusinessErrorCode.STATE_CONFLICT);
        Fixture otherOrder = fixture("10", "10");
        DeliveryLine foreign = postedDelivery(otherOrder, "5").get(0);
        assertCode(() -> service.preview(preview(f, List.of(line(foreign, "1")))), ValidationErrorCode.INVALID_INPUT);
    }

    /** FE A36: two drafts racing for the same delivered quantity queue on the order lock — one wins, never both. */
    @Test
    void concurrentCreates_onTheSameDeliveredQuantity_oneWins_theOtherIsOverQuota() throws Exception {
        Fixture f = fixture("10", "10");
        DeliveryLine delivered = postedDelivery(f, "5").get(0);
        List<SalesInvoiceLineRequest> lines = List.of(line(delivered, "3"));
        String base = service.preview(preview(f, lines)).billingSnapshotVersion();
        SalesInvoiceCreateRequest request = create(f, base, lines);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String k = key("race-" + i);
            Callable<Object> task = () -> {
                start.await();
                try {
                    return service.create(request, k);
                } catch (AppException e) {
                    return e.getErrorCode();
                }
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(outcomes).filteredOn(o -> o instanceof CommercialCommandResult).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o == BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED).hasSize(1);
    }

    @Test
    void database_refusesANonPositiveLineAndADueDateBeforeTheDocumentDate() {
        Fixture f = fixture("10", "10");
        DeliveryLine delivered = postedDelivery(f, "5").get(0);
        CommercialCommandResult draft = createPriced(f, delivered, "1");
        UUID lineId = invoiceLineRepository.findByInvoiceInvoiceIdOrderByLineNo(draft.documentId()).get(0).getInvoiceLineId();
        assertThatThrownBy(() -> jdbc.update("update sales_invoice_lines set quantity = 0 where invoice_line_id = ?", lineId))
                .hasMessageContaining("chk_sales_invoice_lines_quantity");
        assertThatThrownBy(() -> jdbc.update("update sales_invoices set due_date = document_date - 1 where invoice_id = ?",
                draft.documentId())).hasMessageContaining("chk_sales_invoices_due_date");
        assertThatThrownBy(() -> jdbc.update("update sales_invoices set status = 'POSTED' where invoice_id = ?",
                draft.documentId())).hasMessageContaining("chk_sales_invoices_posted");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────────────────────

    private record Fixture(Company company, Plant plant, Warehouse warehouse, Item item, Customer customer, SalesOrder order) {}

    private Fixture fixture(String ordered, String unitPrice) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("IC_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("IP_" + suffix).name("Plant")
                .timezone("Asia/Ho_Chi_Minh").status(OrganizationStatus.ACTIVE).build());
        Warehouse warehouse = warehouseRepository.save(Warehouse.builder().plant(plant).code("IW_" + suffix)
                .name("FG").type(WarehouseType.FINISHED_GOODS).status(OrganizationStatus.ACTIVE).build());
        Item item = itemRepository.save(Item.builder().company(company).code("II_" + suffix).name("Bike")
                .type(ItemType.FINISHED_GOOD).unit("PCS").status(ItemStatus.ACTIVE).build());
        Customer customer = customerRepository.save(Customer.builder().company(company).code("ICU_" + suffix)
                .name("Customer " + suffix).status(CustomerStatus.ACTIVE).build());
        SalesOrder order = SalesOrder.builder().company(company).plant(plant)
                .orderNo("SO-" + UUID.randomUUID().toString().substring(0, 8)).customerId(customer.getCustomerId())
                .customerName(customer.getName()).orderDate(LocalDate.now(PLANT_ZONE).minusDays(5))
                .status(SalesOrderStatus.CONFIRMED).build();
        order.getLines().add(SalesOrderLine.builder().salesOrder(order).item(item).lineNo(1)
                .orderedQuantity(new BigDecimal(ordered)).dueDate(LocalDate.now().plusDays(10))
                .unitPrice(unitPrice == null ? null : new BigDecimal(unitPrice)).build());
        return new Fixture(company, plant, warehouse, item, customer, salesOrderRepository.saveAndFlush(order));
    }

    private List<DeliveryLine> postedDelivery(Fixture f, String... quantities) {
        return delivery(f, true, quantities);
    }

    private List<DeliveryLine> delivery(Fixture f, boolean posted, String... quantities) {
        Delivery delivery = Delivery.builder().code("DN-IT-" + UUID.randomUUID().toString().substring(0, 8))
                .companyId(f.company.getCompanyId()).plantId(f.plant.getPlantId())
                .salesOrderId(f.order.getSalesOrderId()).customerId(f.customer.getCustomerId())
                .customerName(f.customer.getName()).documentDate(LocalDate.now(PLANT_ZONE).minusDays(1)).build();
        if (posted) {
            delivery.post(Instant.now(), USER);
        }
        Delivery saved = deliveryRepository.saveAndFlush(delivery);
        List<DeliveryLine> lines = new ArrayList<>();
        for (int i = 0; i < quantities.length; i++) {
            lines.add(DeliveryLine.builder().delivery(saved).lineNo(i + 1)
                    .salesOrderLineId(f.order.getLines().get(0).getSalesOrderLineId()).itemId(f.item.getItemId())
                    .warehouseId(f.warehouse.getWarehouseId()).quantity(new BigDecimal(quantities[i])).build());
        }
        return deliveryLineRepository.saveAllAndFlush(lines);
    }

    private CommercialCommandResult createPriced(Fixture f, DeliveryLine delivered, String quantity) {
        List<SalesInvoiceLineRequest> lines = List.of(line(delivered, quantity));
        return service.create(create(f, service.preview(preview(f, lines)).billingSnapshotVersion(), lines), key("c"));
    }

    private static SalesInvoiceLineRequest line(DeliveryLine delivered, String quantity) {
        return new SalesInvoiceLineRequest(delivered.getDeliveryLineId(), new BigDecimal(quantity));
    }

    private SalesInvoicePreviewRequest preview(Fixture f, List<SalesInvoiceLineRequest> lines) {
        return new SalesInvoicePreviewRequest(f.order.getSalesOrderId(), orderVersion(f), null, lines);
    }

    private SalesInvoiceCreateRequest create(Fixture f, String snapshot, List<SalesInvoiceLineRequest> lines) {
        LocalDate today = LocalDate.now(PLANT_ZONE);
        return new SalesInvoiceCreateRequest(f.order.getSalesOrderId(), orderVersion(f), snapshot, today,
                today.plusDays(30), "IT", lines);
    }

    private static SalesInvoiceUpdateRequest update(Long version, String snapshot, List<SalesInvoiceLineRequest> lines) {
        LocalDate today = LocalDate.now(PLANT_ZONE);
        return new SalesInvoiceUpdateRequest(version, snapshot, today, today.plusDays(15), "edited", lines);
    }

    private long orderVersion(Fixture f) {
        return salesOrderRepository.findById(f.order.getSalesOrderId()).orElseThrow().getVersion();
    }

    private long countMovements() {
        return jdbc.queryForObject("select count(*) from stock_movements", Long.class);
    }

    private static String key(String name) {
        return "IT-" + name + "-" + UUID.randomUUID();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
