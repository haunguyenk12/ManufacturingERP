package com.erp.manufacturing.module.sales.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.service.SalesOrderAllocationTarget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the planning-demand eligibility JPQL against a real Postgres Testcontainer.
 *
 * <p>All four conditions of spec §2.1 (order status, plant, {@code dueDate <= horizonEnd},
 * {@code openQuantity > 0}) live in the query, not in Java — so per rule {@code R7} they can only
 * be tested for real here; a mocked repository would assert nothing about them. Each exclusion has
 * its own test, and each seeds one eligible line alongside the excluded one so an empty result
 * caused by broken setup cannot pass as a correct exclusion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SalesOrderLineRepositoryIT extends AbstractPostgresIntegrationTest {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate HORIZON_END = LocalDate.of(2026, 8, 31);
    private static final Set<SalesOrderStatus> ELIGIBLE_STATUSES = Set.of(
            SalesOrderStatus.CONFIRMED,
            SalesOrderStatus.IN_PRODUCTION,
            SalesOrderStatus.PARTIALLY_FULFILLED);

    @Autowired
    SalesOrderLineRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void findEligiblePlanningDemands_returnsOpenQuantityNotOrderedQuantity() {
        Plant plant = persistPlant();
        Item item = persistItem(plant.getCompany());
        SalesOrder order = persistOrder(plant, "SO-001", SalesOrderStatus.PARTIALLY_FULFILLED);
        persistLine(order, item, 1, new BigDecimal("100.000000"), new BigDecimal("30.000000"),
                ORDER_DATE.plusDays(10));

        List<SalesOrderPlanningDemandProjection> demands =
                repository.findEligiblePlanningDemands(plant.getPlantId(), HORIZON_END, ELIGIBLE_STATUSES);

        assertThat(demands).hasSize(1);
        SalesOrderPlanningDemandProjection demand = demands.get(0);
        assertThat(demand.getSalesOrderCode()).isEqualTo("SO-001");
        assertThat(demand.getLineNo()).isEqualTo(1);
        assertThat(demand.getItemSku()).isEqualTo(item.getCode());
        assertThat(demand.getUom()).isEqualTo("EA");
        assertThat(demand.getOpenQuantity()).isEqualByComparingTo("70");  // 100 ordered − 30 fulfilled
        assertThat(demand.getDueDate()).isEqualTo(ORDER_DATE.plusDays(10));
    }

    @Test
    void findEligiblePlanningDemands_excludesDraftCancelledAndFulfilledOrders() {
        Plant plant = persistPlant();
        Item item = persistItem(plant.getCompany());
        persistLine(persistOrder(plant, "SO-DRAFT", SalesOrderStatus.DRAFT), item, 1,
                new BigDecimal("10.000000"), BigDecimal.ZERO, ORDER_DATE.plusDays(5));
        persistLine(persistOrder(plant, "SO-CANCELLED", SalesOrderStatus.CANCELLED), item, 1,
                new BigDecimal("10.000000"), BigDecimal.ZERO, ORDER_DATE.plusDays(5));
        persistLine(persistOrder(plant, "SO-FULFILLED", SalesOrderStatus.FULFILLED), item, 1,
                new BigDecimal("10.000000"), BigDecimal.ZERO, ORDER_DATE.plusDays(5));
        persistLine(persistOrder(plant, "SO-OK", SalesOrderStatus.CONFIRMED), item, 1,
                new BigDecimal("10.000000"), BigDecimal.ZERO, ORDER_DATE.plusDays(5));

        List<SalesOrderPlanningDemandProjection> demands =
                repository.findEligiblePlanningDemands(plant.getPlantId(), HORIZON_END, ELIGIBLE_STATUSES);

        assertThat(demands).extracting(SalesOrderPlanningDemandProjection::getSalesOrderCode)
                .containsExactly("SO-OK");
    }

    @Test
    void findEligiblePlanningDemands_excludesOrdersOfAnotherPlant() {
        Plant plant = persistPlant();
        Plant otherPlant = persistPlantIn(plant.getCompany());
        Item item = persistItem(plant.getCompany());
        persistLine(persistOrder(otherPlant, "SO-OTHER-PLANT", SalesOrderStatus.CONFIRMED), item, 1,
                new BigDecimal("10.000000"), BigDecimal.ZERO, ORDER_DATE.plusDays(5));
        persistLine(persistOrder(plant, "SO-OK", SalesOrderStatus.CONFIRMED), item, 1,
                new BigDecimal("10.000000"), BigDecimal.ZERO, ORDER_DATE.plusDays(5));

        List<SalesOrderPlanningDemandProjection> demands =
                repository.findEligiblePlanningDemands(plant.getPlantId(), HORIZON_END, ELIGIBLE_STATUSES);

        assertThat(demands).extracting(SalesOrderPlanningDemandProjection::getSalesOrderCode)
                .containsExactly("SO-OK");
    }

    @Test
    void findEligiblePlanningDemands_excludesLinesDueAfterHorizonEnd() {
        Plant plant = persistPlant();
        Item item = persistItem(plant.getCompany());
        SalesOrder order = persistOrder(plant, "SO-001", SalesOrderStatus.CONFIRMED);
        persistLine(order, item, 1, new BigDecimal("10.000000"), BigDecimal.ZERO, HORIZON_END);
        persistLine(order, item, 2, new BigDecimal("10.000000"), BigDecimal.ZERO, HORIZON_END.plusDays(1));

        List<SalesOrderPlanningDemandProjection> demands =
                repository.findEligiblePlanningDemands(plant.getPlantId(), HORIZON_END, ELIGIBLE_STATUSES);

        // The horizon boundary is inclusive: due exactly on horizonEnd still plans.
        assertThat(demands).extracting(SalesOrderPlanningDemandProjection::getLineNo)
                .containsExactly(1);
    }

    @Test
    void findEligiblePlanningDemands_excludesFullyFulfilledLines() {
        Plant plant = persistPlant();
        Item item = persistItem(plant.getCompany());
        SalesOrder order = persistOrder(plant, "SO-001", SalesOrderStatus.PARTIALLY_FULFILLED);
        persistLine(order, item, 1, new BigDecimal("10.000000"), new BigDecimal("10.000000"),
                ORDER_DATE.plusDays(5));
        persistLine(order, item, 2, new BigDecimal("10.000000"), new BigDecimal("9.999999"),
                ORDER_DATE.plusDays(5));

        List<SalesOrderPlanningDemandProjection> demands =
                repository.findEligiblePlanningDemands(plant.getPlantId(), HORIZON_END, ELIGIBLE_STATUSES);

        assertThat(demands).extracting(SalesOrderPlanningDemandProjection::getLineNo)
                .containsExactly(2);
        assertThat(demands.get(0).getOpenQuantity()).isEqualByComparingTo("0.000001");
    }

    /**
     * F6: the allocation-target query uses a JPQL constructor expression, which fails at runtime
     * rather than at compile time if the record's parameter list drifts. Only a real query can
     * catch that, so it is verified here rather than in a mocked test.
     */
    @Test
    void findAllocationTargets_projectsLineAndOrderIntoTheCrossModuleRecord() {
        Plant plant = persistPlant();
        Item item = persistItem(plant.getCompany());
        SalesOrder order = persistOrder(plant, "SO-001", SalesOrderStatus.IN_PRODUCTION);
        SalesOrderLine line = persistLine(order, item, 2, new BigDecimal("100.000000"),
                new BigDecimal("30.000000"), ORDER_DATE.plusDays(10));
        // A second line that is not asked for, proving the id filter actually filters.
        persistLine(order, item, 3, new BigDecimal("5.000000"), BigDecimal.ZERO, ORDER_DATE.plusDays(11));

        List<SalesOrderAllocationTarget> targets =
                repository.findAllocationTargets(List.of(line.getSalesOrderLineId()));

        assertThat(targets).hasSize(1);
        SalesOrderAllocationTarget target = targets.get(0);
        assertThat(target.salesOrderLineId()).isEqualTo(line.getSalesOrderLineId());
        assertThat(target.salesOrderId()).isEqualTo(order.getSalesOrderId());
        assertThat(target.salesOrderCode()).isEqualTo("SO-001");
        assertThat(target.lineNo()).isEqualTo(2);
        assertThat(target.dueDate()).isEqualTo(ORDER_DATE.plusDays(10));
        assertThat(target.uom()).isEqualTo("EA");
        assertThat(target.openQuantity()).isEqualByComparingTo("70");  // 100 ordered − 30 fulfilled
    }

    @Test
    void findOrderIdsByLineIds_deduplicatesLinesOfTheSameOrder() {
        Plant plant = persistPlant();
        Item item = persistItem(plant.getCompany());
        SalesOrder order = persistOrder(plant, "SO-001", SalesOrderStatus.CONFIRMED);
        SalesOrderLine first = persistLine(order, item, 1, new BigDecimal("10.000000"),
                BigDecimal.ZERO, ORDER_DATE.plusDays(5));
        SalesOrderLine second = persistLine(order, item, 2, new BigDecimal("10.000000"),
                BigDecimal.ZERO, ORDER_DATE.plusDays(5));

        List<UUID> orderIds = repository.findOrderIdsByLineIds(
                List.of(first.getSalesOrderLineId(), second.getSalesOrderLineId()));

        assertThat(orderIds).containsExactly(order.getSalesOrderId());
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
        return persistPlantIn(entityManager.persistFlushFind(company));
    }

    private Plant persistPlantIn(Company company) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Plant plant = Plant.builder().company(company).code("PL_" + suffix).name("Plant " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        plant.setCreatedAt(now);
        plant.setUpdatedAt(now);
        return entityManager.persistFlushFind(plant);
    }

    private Item persistItem(Company company) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Item item = Item.builder().company(company).code("ITEM_" + suffix).name("Item " + suffix)
                .type(ItemType.FINISHED_GOOD).unit("EA").lotTracked(true).status(ItemStatus.ACTIVE).build();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return entityManager.persistFlushFind(item);
    }

    private SalesOrder persistOrder(Plant plant, String orderNo, SalesOrderStatus status) {
        Instant now = Instant.now();

        SalesOrder order = SalesOrder.builder()
                .company(plant.getCompany())
                .plant(plant)
                .orderNo(orderNo)
                .customerName("ACME Corp")
                .orderDate(ORDER_DATE)
                .status(status)
                .build();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return entityManager.persistFlushFind(order);
    }

    private SalesOrderLine persistLine(SalesOrder order, Item item, int lineNo,
                                       BigDecimal ordered, BigDecimal fulfilled, LocalDate dueDate) {
        Instant now = Instant.now();

        SalesOrderLine line = SalesOrderLine.builder()
                .salesOrder(order)
                .item(item)
                .lineNo(lineNo)
                .orderedQuantity(ordered)
                .fulfilledQuantity(fulfilled)
                .dueDate(dueDate)
                .build();
        line.setCreatedAt(now);
        line.setUpdatedAt(now);
        return entityManager.persistFlushFind(line);
    }
}
