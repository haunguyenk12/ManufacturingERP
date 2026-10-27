package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.billing.domain.CustomerPayment;
import com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus;
import com.erp.manufacturing.module.billing.domain.PaymentMethod;
import com.erp.manufacturing.module.billing.domain.PaymentState;
import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentAllocationRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentCancelRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentCreateRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentPostRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentUpdateRequest;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentAllocationRepository;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
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
import org.springframework.data.domain.PageRequest;
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
 * G10d customer payments on a real database (FE R16–R19, A24–A33, A37). Only Postgres answers these: whether paid and
 * claimed amounts are summed from the stored allocations of other payments; whether the invoice locks serialise two
 * drafts competing for the same outstanding; whether posting moves each paid invoice's version by exactly one; and
 * whether the payment-state filter of the invoice list reads POSTED payments only. The wrapping {@code @DataJpaTest}
 * transaction is off — each service call commits on its own, as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CustomerPaymentService.class, CustomerPaymentSupport.class, SalesOrderCommercialLookupService.class,
        OrganizationLookupService.class, CommercialIdempotency.class, IdempotencySupport.class, CustomerPaymentIT.Config.class})
class CustomerPaymentIT extends AbstractPostgresIntegrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-0000000010d0");
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

    @Autowired CustomerPaymentService service;
    @Autowired CustomerPaymentRepository paymentRepository;
    @Autowired CustomerPaymentAllocationRepository allocationRepository;
    @Autowired SalesInvoiceRepository invoiceRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired JdbcTemplate jdbc;

    @MockBean SecurityAuditorAware auditor;

    @BeforeEach
    void actor() {
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(USER));
    }

    /** FE A24–A26: 300 000 of 800 000 leaves 500 000; the post moves the invoice version by one; a second pays it off. */
    @Test
    void postPaysTheInvoice_movesItsVersionByOne_andASecondPaymentPaysItOff() {
        Fixture f = fixture();
        SalesInvoice invoice = postedInvoice(f, "800000");
        long invoiceVersion = invoiceVersion(invoice);

        CommercialCommandResult first = service.create(create(f, "300000", alloc(invoice, "300000")), key("c1"));
        CustomerPayment draft = paymentRepository.findById(first.documentId()).orElseThrow();
        assertThat(draft.getCode()).matches("PAY-\\d{6}");
        assertThat(draft.getStatus()).isEqualTo(CustomerPaymentStatus.DRAFT);
        assertThat(paid(invoice)).as("a draft pays nothing").isEqualByComparingTo("0");
        assertThat(invoiceVersion(invoice)).as("a draft never touches the invoice").isEqualTo(invoiceVersion);

        String postKey = key("p1");
        CommercialCommandResult posted = service.post(first.documentId(), new CustomerPaymentPostRequest(first.version()), postKey);
        assertThat(posted.version()).isEqualTo(first.version() + 1);
        assertThat(paid(invoice)).isEqualByComparingTo("300000");
        assertThat(invoiceVersion(invoice)).as("posting moves each paid invoice by exactly one").isEqualTo(invoiceVersion + 1);
        assertThat(state(invoice)).isEqualTo(PaymentState.PARTIALLY_PAID);
        assertThat(service.post(first.documentId(), new CustomerPaymentPostRequest(first.version()), postKey).version())
                .as("a replay is the same answer, posted once").isEqualTo(posted.version());
        assertThat(invoiceVersion(invoice)).isEqualTo(invoiceVersion + 1);

        CommercialCommandResult second = service.create(create(f, "500000", alloc(invoice, "500000")), key("c2"));
        service.post(second.documentId(), new CustomerPaymentPostRequest(second.version()), key("p2"));
        assertThat(state(invoice)).isEqualTo(PaymentState.PAID);
    }

    /** FE A27/A31: outstanding minus other drafts; an edit excludes its own claim; cancel returns the claim once. */
    @Test
    void allocation_isOutstandingMinusOtherDrafts_editExcludesItsOwnClaim_andCancelReturnsIt() {
        Fixture f = fixture();
        SalesInvoice invoice = postedInvoice(f, "1000");
        CommercialCommandResult paidPart = service.create(create(f, "200", alloc(invoice, "200")), key("a"));
        service.post(paidPart.documentId(), new CustomerPaymentPostRequest(paidPart.version()), key("ap"));
        CommercialCommandResult other = service.create(create(f, "300", alloc(invoice, "300")), key("b"));

        assertCode(() -> service.create(create(f, "501", alloc(invoice, "501")), key("over")),
                BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
        CommercialCommandResult mine = service.create(create(f, "400", alloc(invoice, "400")), key("m"));
        CommercialCommandResult edited = service.update(mine.documentId(), update(mine.version(), "500", alloc(invoice, "500")), key("e"));
        assertThat(edited.version()).as("an edit moves the version by exactly one").isEqualTo(mine.version() + 1);
        assertCode(() -> service.update(mine.documentId(), update(edited.version(), "501", alloc(invoice, "501")), key("e2")),
                BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
        // B112: only the allocations change (same header) — the version still moves by exactly one.
        SalesInvoice second = postedInvoice(f, "1000");
        CommercialCommandResult moved = service.update(mine.documentId(), update(edited.version(), "500", alloc(second, "500")), key("mv"));
        assertThat(moved.version()).isEqualTo(edited.version() + 1);

        CommercialCommandResult cancelled = service.cancel(other.documentId(),
                new CustomerPaymentCancelRequest(other.version(), "customer paid by cash instead"), key("x"));
        assertThat(cancelled.version()).isEqualTo(other.version() + 1);
        assertThat(paymentRepository.findById(other.documentId()).orElseThrow().getStatus()).isEqualTo(CustomerPaymentStatus.CANCELLED);
        assertThat(service.create(create(f, "300", alloc(invoice, "300")), key("free"))).as("the claim is free again").isNotNull();
        assertCode(() -> service.cancel(other.documentId(),
                new CustomerPaymentCancelRequest(cancelled.version(), "once more please"), key("x2")), BusinessErrorCode.STATE_CONFLICT);
    }

    /** FE A28/R16: header = sum; same customer; POSTED invoices only; the invoice version the user saw. */
    @Test
    void anAllocationMustBeAPostedInvoiceOfThisCustomer_atTheVersionSeen_andTheAmountMustAddUp() {
        Fixture f = fixture();
        SalesInvoice invoice = postedInvoice(f, "1000");
        long payments = paymentRepository.count();

        assertCode(() -> service.create(create(f, "999", alloc(invoice, "100")), key("sum")), ValidationErrorCode.INVALID_INPUT);
        Fixture stranger = fixture();
        SalesInvoice foreign = postedInvoice(stranger, "1000");
        assertCode(() -> service.create(create(f, "100", alloc(foreign, "100")), key("cust")), ValidationErrorCode.INVALID_INPUT);
        SalesInvoice draft = invoice(f, "1000", false);
        assertCode(() -> service.create(create(f, "100", alloc(draft, "100")), key("draft")), BusinessErrorCode.STATE_CONFLICT);
        assertCode(() -> service.create(create(f, "100", new CustomerPaymentAllocationRequest(invoice.getInvoiceId(),
                invoiceVersion(invoice) + 1, new BigDecimal("100"))), key("ver")), BusinessErrorCode.CONCURRENT_MODIFICATION);
        assertCode(() -> service.create(new CustomerPaymentCreateRequest(f.customer.getCustomerId(),
                LocalDate.now(PLANT_ZONE).minusDays(30), PaymentMethod.CASH, null, null, new BigDecimal("100"),
                List.of(alloc(invoice, "100"))), key("date")), ValidationErrorCode.INVALID_INPUT);
        assertThat(paymentRepository.count()).as("nothing saved").isEqualTo(payments);
    }

    /** FE A29/A32: one payment over two invoices; only the author edits or cancels; a posted payment is final. */
    @Test
    void onePaymentPaysTwoInvoices_onlyTheAuthorChangesADraft_andPostedIsFinal() {
        Fixture f = fixture();
        SalesInvoice a = postedInvoice(f, "600");
        SalesInvoice b = postedInvoice(f, "400");
        CommercialCommandResult payment = service.create(create(f, "700", alloc(a, "600"), alloc(b, "100")), key("two"));
        assertThat(allocationRepository.findByPaymentPaymentIdOrderByLineNo(payment.documentId()))
                .extracting(x -> x.getAmount().toPlainString()).containsExactly("600", "100");

        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        assertCode(() -> service.cancel(payment.documentId(), new CustomerPaymentCancelRequest(payment.version(), "not my draft"),
                key("o")), AuthErrorCode.ACCESS_DENIED);
        // FE Q28: someone else with POST may post the author's draft.
        CommercialCommandResult posted = service.post(payment.documentId(), new CustomerPaymentPostRequest(payment.version()), key("p"));
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(USER));

        assertThat(state(a)).isEqualTo(PaymentState.PAID);
        assertThat(state(b)).isEqualTo(PaymentState.PARTIALLY_PAID);
        assertCode(() -> service.cancel(payment.documentId(), new CustomerPaymentCancelRequest(posted.version(), "too late now"),
                key("x")), BusinessErrorCode.STATE_CONFLICT);
        assertCode(() -> service.update(payment.documentId(), update(posted.version(), "600", alloc(a, "600")), key("u")),
                BusinessErrorCode.STATE_CONFLICT);
    }

    /** FE R18: a draft saved on yesterday's outstanding is re-checked at post — another posted payment wins. */
    @Test
    void postReChecksTheOutstanding_underTheInvoiceLock() {
        Fixture f = fixture();
        SalesInvoice invoice = postedInvoice(f, "1000");
        CommercialCommandResult one = service.create(create(f, "600", alloc(invoice, "600")), key("1"));
        CommercialCommandResult two = service.create(create(f, "400", alloc(invoice, "400")), key("2"));
        service.post(one.documentId(), new CustomerPaymentPostRequest(one.version()), key("p1"));
        // Push the invoice amount down behind the service's back: the second draft no longer fits.
        jdbc.update("update sales_invoices set amount = 900 where invoice_id = ?", invoice.getInvoiceId());
        assertCode(() -> service.post(two.documentId(), new CustomerPaymentPostRequest(two.version()), key("p2")),
                BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
        assertThat(paymentRepository.findById(two.documentId()).orElseThrow().getStatus()).isEqualTo(CustomerPaymentStatus.DRAFT);
    }

    /** FE §3.2/Q8: the invoice list's payment-state filter reads POSTED payments only; DRAFT invoices never match. */
    @Test
    void paymentStateFilter_readsPostedPaymentsOnly() {
        Fixture f = fixture();
        SalesInvoice unpaid = postedInvoice(f, "100");
        SalesInvoice partly = postedInvoice(f, "100");
        SalesInvoice paidOff = postedInvoice(f, "100");
        SalesInvoice zero = postedInvoice(f, "0");
        invoice(f, "100", false);
        CommercialCommandResult p = service.create(create(f, "150", alloc(partly, "50"), alloc(paidOff, "100")), key("p"));
        service.post(p.documentId(), new CustomerPaymentPostRequest(p.version()), key("pp"));
        service.create(create(f, "100", alloc(unpaid, "100")), key("draft-only"));

        assertThat(filter(f, PaymentState.UNPAID)).containsExactly(unpaid.getInvoiceId());
        assertThat(filter(f, PaymentState.PARTIALLY_PAID)).containsExactly(partly.getInvoiceId());
        assertThat(filter(f, PaymentState.PAID)).containsExactly(paidOff.getInvoiceId());
        assertThat(filter(f, PaymentState.NO_AMOUNT_DUE)).containsExactly(zero.getInvoiceId());
    }

    /** FE A37: two drafts racing for the same outstanding queue on the invoice lock — one wins, never both. */
    @Test
    void concurrentCreates_onTheSameOutstanding_oneWins_theOtherIsOver() throws Exception {
        Fixture f = fixture();
        SalesInvoice invoice = postedInvoice(f, "1000");
        CustomerPaymentCreateRequest request = create(f, "700", alloc(invoice, "700"));
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
    void database_refusesANonPositiveAllocationAndAPostedPaymentWithoutAPostingTime() {
        Fixture f = fixture();
        SalesInvoice invoice = postedInvoice(f, "1000");
        CommercialCommandResult draft = service.create(create(f, "10", alloc(invoice, "10")), key("d"));
        assertThatThrownBy(() -> jdbc.update("update customer_payment_allocations set amount = 0 where payment_id = ?",
                draft.documentId())).hasMessageContaining("chk_customer_payment_allocations_amount");
        assertThatThrownBy(() -> jdbc.update("update customer_payments set status = 'POSTED' where payment_id = ?",
                draft.documentId())).hasMessageContaining("chk_customer_payments_posted");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────────────────────

    private record Fixture(Company company, Plant plant, Customer customer, SalesOrder order) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("PC_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("PP_" + suffix).name("Plant")
                .timezone("Asia/Ho_Chi_Minh").status(OrganizationStatus.ACTIVE).build());
        Customer customer = customerRepository.save(Customer.builder().company(company).code("PCU_" + suffix)
                .name("Customer " + suffix).status(CustomerStatus.ACTIVE).build());
        SalesOrder order = salesOrderRepository.saveAndFlush(SalesOrder.builder().company(company).plant(plant)
                .orderNo("SO-" + suffix).customerId(customer.getCustomerId()).customerName(customer.getName())
                .orderDate(LocalDate.now(PLANT_ZONE).minusDays(5)).status(SalesOrderStatus.CONFIRMED).build());
        return new Fixture(company, plant, customer, order);
    }

    private SalesInvoice postedInvoice(Fixture f, String amount) {
        return invoice(f, amount, true);
    }

    private SalesInvoice invoice(Fixture f, String amount, boolean posted) {
        LocalDate date = LocalDate.now(PLANT_ZONE).minusDays(2);
        SalesInvoice invoice = SalesInvoice.builder().code("INV-IT-" + UUID.randomUUID().toString().substring(0, 8))
                .companyId(f.company.getCompanyId()).plantId(f.plant.getPlantId())
                .salesOrderId(f.order.getSalesOrderId()).customerId(f.customer.getCustomerId())
                .customerName(f.customer.getName()).documentDate(date).dueDate(date.plusDays(30))
                .amount(new BigDecimal(amount)).billingSnapshot("BILL-it").build();
        if (posted) {
            invoice.post(Instant.now(), USER);
        }
        return invoiceRepository.saveAndFlush(invoice);
    }

    private CustomerPaymentAllocationRequest alloc(SalesInvoice invoice, String amount) {
        return new CustomerPaymentAllocationRequest(invoice.getInvoiceId(), invoiceVersion(invoice), new BigDecimal(amount));
    }

    private static CustomerPaymentCreateRequest create(Fixture f, String amount, CustomerPaymentAllocationRequest... allocations) {
        return new CustomerPaymentCreateRequest(f.customer.getCustomerId(), LocalDate.now(PLANT_ZONE), PaymentMethod.TRANSFER,
                "IT-REF", null, new BigDecimal(amount), List.of(allocations));
    }

    private static CustomerPaymentUpdateRequest update(Long version, String amount, CustomerPaymentAllocationRequest... allocations) {
        return new CustomerPaymentUpdateRequest(version, LocalDate.now(PLANT_ZONE), PaymentMethod.CASH, null, "edited",
                new BigDecimal(amount), List.of(allocations));
    }

    private long invoiceVersion(SalesInvoice invoice) {
        return invoiceRepository.findById(invoice.getInvoiceId()).orElseThrow().getVersion();
    }

    private BigDecimal paid(SalesInvoice invoice) {
        return allocationRepository.sumPostedByInvoiceIds(List.of(invoice.getInvoiceId())).stream()
                .map(CustomerPaymentAllocationRepository.InvoiceAmountProjection::getAmount)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private PaymentState state(SalesInvoice invoice) {
        return PaymentState.of(invoiceRepository.findById(invoice.getInvoiceId()).orElseThrow().getAmount(), paid(invoice));
    }

    private List<UUID> filter(Fixture f, PaymentState state) {
        return invoiceRepository.search(f.plant.getPlantId(), null, null, null, null, null, null, state.name(),
                PageRequest.of(0, 50)).map(SalesInvoice::getInvoiceId).getContent();
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
