package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderUpdateRequest;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Full-replacement {@code PATCH /sales-orders/{id}} against a real Postgres.
 *
 * <p>Rule {@code R7}: the defect this guards is invisible to a mocked repository. Replacement lines
 * necessarily reuse {@code lineNo} 1..N ({@code B87}), and in a single Hibernate flush the child
 * {@code INSERT}s are executed before the orphan-removal {@code DELETE}s — so the write died on
 * {@code uk_sales_order_lines_order_line_no} while every unit test stayed green (FE defect report
 * 2026-08-10, {@code B112}). Only a real flush against the real constraint proves the fix.
 *
 * <p>The service is imported into a {@code @DataJpaTest} slice rather than a full context: what is
 * under test is the persistence ordering, and method security / auditing are covered by
 * {@code SalesOrderServiceTest} and {@code SalesOrderControllerTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SalesOrderService.class, SalesOrderMapper.class,
        SalesOrderUpdateLinesIT.AuditingConfig.class})
class SalesOrderUpdateLinesIT extends AbstractPostgresIntegrationTest {

    /**
     * {@code @DataJpaTest} does not pick up {@code JpaAuditingConfig}, and the lines this test asks
     * the service to build get their {@code createdAt}/{@code updatedAt} (NOT NULL) from auditing —
     * exactly as they do in production. Auditing is enabled here rather than the columns being
     * back-filled by hand, so the insert under test is the production insert.
     */
    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }
    }

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 8, 1);

    @Autowired
    SalesOrderService service;

    @Autowired
    SalesOrderLineRepository lineRepository;

    @Autowired
    TestEntityManager entityManager;

    @MockBean
    OrganizationLookupService organizationLookupService;

    @MockBean
    ItemLookupService itemLookupService;

    @MockBean
    PlanningDemandService planningDemandService;

    private Plant plant;
    private Item item;
    private Item otherItem;
    private SalesOrder order;

    @BeforeEach
    void setUp() {
        plant = persistPlant();
        item = persistItem(plant.getCompany(), "FG-A");
        otherItem = persistItem(plant.getCompany(), "FG-B");
        order = persistDraftOrderWithLines();
        when(itemLookupService.getActiveItem(any())).thenAnswer(invocation -> {
            UUID itemId = invocation.getArgument(0);
            return itemId.equals(item.getItemId()) ? item : otherItem;
        });
    }

    @Test
    void update_replacesLinesReusingLineNumberOne_withoutViolatingTheLineNoUniqueConstraint() {
        SalesOrderResponse response = service.update(order.getSalesOrderId(), new SalesOrderUpdateRequest(
                0L, "FE2 Customer Updated", ORDER_DATE, "Replace demand lines atomically",
                List.of(new SalesOrderLineRequest(otherItem.getItemId(), new BigDecimal("12.000000"),
                        ORDER_DATE.plusDays(11)))));

        assertThat(response.customerName()).isEqualTo("FE2 Customer Updated");
        assertThat(response.lines()).hasSize(1);
        SalesOrderLineResponse line = response.lines().get(0);
        assertThat(line.lineNo()).isEqualTo(1);
        assertThat(line.salesOrderLineId()).isNotNull();
        assertThat(line.itemId()).isEqualTo(otherItem.getItemId());
        assertThat(line.orderedQuantity()).isEqualByComparingTo("12.000000");
        assertThat(line.dueDate()).isEqualTo(ORDER_DATE.plusDays(11));
        // Exactly one write: the response's version is what the client must send next (B111).
        assertThat(response.version()).isEqualTo(1L);

        entityManager.flush();
        entityManager.clear();
        assertThat(persistedLines()).singleElement()
                .satisfies(persisted -> {
                    assertThat(persisted.getLineNo()).isEqualTo(1);
                    assertThat(persisted.getItem().getItemId()).isEqualTo(otherItem.getItemId());
                    assertThat(persisted.getOrderedQuantity()).isEqualByComparingTo("12.000000");
                });
    }

    @Test
    void update_growingFromTwoLinesToThree_numbersThemOneToThree() {
        SalesOrderResponse response = service.update(order.getSalesOrderId(), new SalesOrderUpdateRequest(
                0L, null, null, null,
                List.of(new SalesOrderLineRequest(item.getItemId(), new BigDecimal("1.000000"),
                                ORDER_DATE.plusDays(5)),
                        new SalesOrderLineRequest(otherItem.getItemId(), new BigDecimal("2.000000"),
                                ORDER_DATE.plusDays(6)),
                        new SalesOrderLineRequest(item.getItemId(), new BigDecimal("3.000000"),
                                ORDER_DATE.plusDays(7)))));

        assertThat(response.lines()).extracting(SalesOrderLineResponse::lineNo)
                .containsExactly(1, 2, 3);

        entityManager.flush();
        entityManager.clear();
        assertThat(persistedLines()).extracting(SalesOrderLine::getLineNo).containsExactly(1, 2, 3);
    }

    /**
     * Found in the same smoke test as the constraint violation: {@code lines} is an inverse
     * ({@code mappedBy}) collection, so replacing it alone left the header row untouched and
     * {@code version} frozen — two concurrent replacements would both pass the {@code expectedVersion}
     * check and the second would silently win. Only a real {@code UPDATE} against a real row shows
     * this, which is why it is pinned here and not in a mocked test.
     */
    @Test
    void update_replacingOnlyTheLines_stillBumpsTheVersionExactlyOnce() {
        SalesOrderResponse response = service.update(order.getSalesOrderId(), new SalesOrderUpdateRequest(
                0L, null, null, null,
                List.of(new SalesOrderLineRequest(item.getItemId(), new BigDecimal("7.000000"),
                        ORDER_DATE.plusDays(9)))));

        assertThat(response.version()).isEqualTo(1L);

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(SalesOrder.class, order.getSalesOrderId()).getVersion())
                .isEqualTo(1L);
    }

    /**
     * The replacement is rejected before anything is written, so the mid-method {@code flush()}
     * never runs and the original lines are untouched. The harder case — an invalid line that is
     * only caught <em>after</em> the deletes have flushed — cannot be asserted from inside this
     * slice (the test's own transaction never commits), so it is pinned at the unit level instead:
     * {@code SalesOrderServiceTest.update_invalidReplacementLine_flushesTheDeletesButNeverCommits}.
     */
    @Test
    void update_dueDateBeforeOrderDate_leavesTheExistingLinesInPlace() {
        assertThatThrownBy(() -> service.update(order.getSalesOrderId(), new SalesOrderUpdateRequest(
                0L, "Should not stick", null, null,
                List.of(new SalesOrderLineRequest(item.getItemId(), new BigDecimal("5.000000"),
                        ORDER_DATE.minusDays(1))))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        assertThat(persistedLines()).extracting(SalesOrderLine::getLineNo).containsExactly(1, 2);
    }

    private List<SalesOrderLine> persistedLines() {
        return lineRepository.findAll().stream()
                .filter(line -> line.getSalesOrder().getSalesOrderId().equals(order.getSalesOrderId()))
                .sorted(java.util.Comparator.comparing(SalesOrderLine::getLineNo))
                .toList();
    }

    // @DataJpaTest does not load JpaAuditingConfig/SecurityAuditorAware, so createdAt/updatedAt
    // (NOT NULL on BaseEntity subclasses) are set explicitly before persisting.

    private Plant persistPlant() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Company company = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        Company persistedCompany = entityManager.persistFlushFind(company);

        Plant newPlant = Plant.builder().company(persistedCompany).code("PL_" + suffix)
                .name("Plant " + suffix).status(OrganizationStatus.ACTIVE).build();
        newPlant.setCreatedAt(now);
        newPlant.setUpdatedAt(now);
        return entityManager.persistFlushFind(newPlant);
    }

    private Item persistItem(Company company, String prefix) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Item newItem = Item.builder().company(company).code(prefix + "_" + suffix)
                .name("Item " + suffix).type(ItemType.FINISHED_GOOD).unit("EA")
                .lotTracked(true).status(ItemStatus.ACTIVE).build();
        newItem.setCreatedAt(now);
        newItem.setUpdatedAt(now);
        return entityManager.persistFlushFind(newItem);
    }

    private SalesOrder persistDraftOrderWithLines() {
        Instant now = Instant.now();

        SalesOrder newOrder = SalesOrder.builder()
                .company(plant.getCompany())
                .plant(plant)
                .orderNo("SO_" + UUID.randomUUID())
                .customerName("ACME Corp")
                .orderDate(ORDER_DATE)
                .status(SalesOrderStatus.DRAFT)
                .build();
        newOrder.setCreatedAt(now);
        newOrder.setUpdatedAt(now);
        SalesOrder persisted = entityManager.persistFlushFind(newOrder);

        persistLine(persisted, item, 1, new BigDecimal("10.000000"), ORDER_DATE.plusDays(5));
        persistLine(persisted, otherItem, 2, new BigDecimal("20.000000"), ORDER_DATE.plusDays(6));
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(SalesOrder.class, persisted.getSalesOrderId());
    }

    private void persistLine(SalesOrder owner, Item lineItem, int lineNo,
                             BigDecimal ordered, LocalDate dueDate) {
        Instant now = Instant.now();

        SalesOrderLine line = SalesOrderLine.builder()
                .salesOrder(owner)
                .item(lineItem)
                .lineNo(lineNo)
                .orderedQuantity(ordered)
                .fulfilledQuantity(BigDecimal.ZERO)
                .dueDate(dueDate)
                .build();
        line.setCreatedAt(now);
        line.setUpdatedAt(now);
        entityManager.persist(line);
    }
}
