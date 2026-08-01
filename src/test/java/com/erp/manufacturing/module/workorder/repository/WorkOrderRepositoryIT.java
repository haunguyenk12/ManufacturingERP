package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.workorder.domain.ProductionReceipt;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptLine;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the two work-order JPQL queries added in F7 against a real Postgres Testcontainer.
 *
 * <p>Both live entirely in the query rather than in Java, so per rule {@code R7} this is the only
 * place they can actually be tested — a mocked repository would happily return whatever the test
 * asked for and prove nothing:
 * <ul>
 *   <li>{@code findExecutionCandidates} (spec §5.1, invariant B75) — in particular the
 *       {@code actualGoodQuantity < plannedQuantity} half, which is the one a status-only filter
 *       would silently drop;</li>
 *   <li>{@code search} (spec §3.2) — the free-text predicate over work order number and product SKU.</li>
 * </ul>
 *
 * <p>F8 adds {@code findReceiptCandidates} (spec §6.2, invariant B76), whose three conditions are
 * likewise pure JPQL, plus the optimistic-locking check spec §10.3 asks for on the cumulative
 * quantities.
 *
 * <p>Every exclusion case also seeds one row that <em>must</em> be returned, so a result that is empty
 * because the fixture is broken cannot pass as a correct exclusion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkOrderRepositoryIT extends AbstractPostgresIntegrationTest {

    private static final List<WorkOrderStatus> CANDIDATE_STATUSES =
            List.of(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS);

    /** {@code canReceipt()} — deliberately includes COMPLETED, unlike {@link #CANDIDATE_STATUSES}. */
    private static final List<WorkOrderStatus> RECEIPT_CANDIDATE_STATUSES = List.of(
            WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED);

    @Autowired
    WorkOrderRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void findExecutionCandidates_returnsReleasedAndInProgressWorkOrdersWithOutputStillToReport() {
        Fixture fixture = persistFixture();

        WorkOrder released = persistWorkOrder(fixture, "WO-REL", WorkOrderStatus.RELEASED,
                new BigDecimal("10.000000"), BigDecimal.ZERO);
        WorkOrder inProgress = persistWorkOrder(fixture, "WO-PROG", WorkOrderStatus.IN_PROGRESS,
                new BigDecimal("10.000000"), new BigDecimal("6.000000"));

        List<WorkOrder> candidates = repository
                .findExecutionCandidates(fixture.plant.getPlantId(), CANDIDATE_STATUSES, PageRequest.of(0, 20))
                .getContent();

        assertThat(candidates).extracting(WorkOrder::getWorkOrderId)
                .containsExactlyInAnyOrder(released.getWorkOrderId(), inProgress.getWorkOrderId());
    }

    /**
     * The half of B75 that is easy to lose. Spec §5.1 forbids cumulative good from passing
     * {@code plannedQuantity}, so a work order sitting exactly on its plan can no longer accept a
     * report — offering it to the shop floor would be offering a button that always fails.
     */
    @Test
    void findExecutionCandidates_excludesWorkOrdersThatAlreadyReachedTheirPlan() {
        Fixture fixture = persistFixture();

        persistWorkOrder(fixture, "WO-DONE", WorkOrderStatus.IN_PROGRESS,
                new BigDecimal("10.000000"), new BigDecimal("10.000000"));
        WorkOrder stillOpen = persistWorkOrder(fixture, "WO-OPEN", WorkOrderStatus.IN_PROGRESS,
                new BigDecimal("10.000000"), new BigDecimal("9.999999"));

        List<WorkOrder> candidates = repository
                .findExecutionCandidates(fixture.plant.getPlantId(), CANDIDATE_STATUSES, PageRequest.of(0, 20))
                .getContent();

        assertThat(candidates).extracting(WorkOrder::getWorkOrderId)
                .containsExactly(stillOpen.getWorkOrderId());
    }

    @Test
    void findExecutionCandidates_excludesStatusesTheShopFloorCannotReportAgainst() {
        Fixture fixture = persistFixture();

        for (WorkOrderStatus refused : List.of(WorkOrderStatus.DRAFT, WorkOrderStatus.PLANNED,
                WorkOrderStatus.BLOCKED, WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED)) {
            persistWorkOrder(fixture, "WO-" + refused, refused,
                    new BigDecimal("10.000000"), BigDecimal.ZERO);
        }
        WorkOrder eligible = persistWorkOrder(fixture, "WO-OK", WorkOrderStatus.RELEASED,
                new BigDecimal("10.000000"), BigDecimal.ZERO);

        List<WorkOrder> candidates = repository
                .findExecutionCandidates(fixture.plant.getPlantId(), CANDIDATE_STATUSES, PageRequest.of(0, 20))
                .getContent();

        assertThat(candidates).extracting(WorkOrder::getWorkOrderId)
                .containsExactly(eligible.getWorkOrderId());
    }

    @Test
    void findExecutionCandidates_excludesAnotherPlant() {
        Fixture fixture = persistFixture();
        Fixture other = persistFixture();

        persistWorkOrder(other, "WO-FOREIGN", WorkOrderStatus.RELEASED,
                new BigDecimal("10.000000"), BigDecimal.ZERO);
        WorkOrder mine = persistWorkOrder(fixture, "WO-MINE", WorkOrderStatus.RELEASED,
                new BigDecimal("10.000000"), BigDecimal.ZERO);

        List<WorkOrder> candidates = repository
                .findExecutionCandidates(fixture.plant.getPlantId(), CANDIDATE_STATUSES, PageRequest.of(0, 20))
                .getContent();

        assertThat(candidates).extracting(WorkOrder::getWorkOrderId)
                .containsExactly(mine.getWorkOrderId());
    }

    // ---------------------------------------------------------------------------------------
    // findReceiptCandidates (spec §6.2, invariant B76) — F8
    // ---------------------------------------------------------------------------------------

    @Test
    void findReceiptCandidates_returnsWorkOrdersWithProducedOutputNotYetWarehoused() {
        Fixture fixture = persistFixture();

        WorkOrder released = persistWorkOrder(fixture, "WO-REL", WorkOrderStatus.RELEASED,
                new BigDecimal("10.000000"), new BigDecimal("4.000000"));
        WorkOrder inProgress = persistWorkOrder(fixture, "WO-PROG", WorkOrderStatus.IN_PROGRESS,
                new BigDecimal("10.000000"), new BigDecimal("6.000000"));
        // Produced nothing yet ⇒ there is nothing to warehouse.
        persistWorkOrder(fixture, "WO-IDLE", WorkOrderStatus.RELEASED,
                new BigDecimal("10.000000"), BigDecimal.ZERO);

        assertThat(receiptCandidates(fixture)).extracting(WorkOrder::getWorkOrderId)
                .containsExactlyInAnyOrder(released.getWorkOrderId(), inProgress.getWorkOrderId());
    }

    /**
     * 🔴 The case that separates B76 from B75, and the one that would rebuild debt #25 (D11) in the
     * read model. {@code reportProduction} closes a work order the moment cumulative good reaches the
     * plan, but {@code completedQuantity} — how much actually reached the racks — lags behind, so the
     * <em>last</em> receipt of every such work order is made against a {@code COMPLETED} one. Filter
     * COMPLETED out and that receipt has no row to start from.
     */
    @Test
    void findReceiptCandidates_includesCompletedWorkOrdersWithOutputStillToWarehouse() {
        Fixture fixture = persistFixture();

        WorkOrder completed = persistWorkOrder(fixture, "WO-DONE", WorkOrderStatus.COMPLETED,
                new BigDecimal("10.000000"), new BigDecimal("10.000000"));

        assertThat(receiptCandidates(fixture)).extracting(WorkOrder::getWorkOrderId)
                .containsExactly(completed.getWorkOrderId());
    }

    /**
     * The half a naive {@code actualGood - completed} filter drops. An open receipt has already
     * claimed the output (B16), so listing the work order again would hand the operator a row that
     * {@code postNew} is certain to refuse with {@code PLANNED_QUANTITY_EXCEEDED}.
     *
     * <p>Both {@code DRAFT} and {@code PENDING_APPROVAL} count; {@code APPROVED} does not, because
     * approval already moved the quantity into {@code completedQuantity} and counting it twice would
     * hide output that genuinely still has to be warehoused.
     */
    @Test
    void findReceiptCandidates_excludesWorkOrdersFullyClaimedByAnOpenDraft() {
        Fixture fixture = persistFixture();

        WorkOrder claimed = persistWorkOrder(fixture, "WO-CLAIMED", WorkOrderStatus.IN_PROGRESS,
                new BigDecimal("10.000000"), new BigDecimal("6.000000"));
        persistReceipt(claimed, ProductionReceiptStatus.DRAFT, new BigDecimal("6.000000"), fixture);

        WorkOrder partlyClaimed = persistWorkOrder(fixture, "WO-PARTIAL", WorkOrderStatus.IN_PROGRESS,
                new BigDecimal("10.000000"), new BigDecimal("6.000000"));
        persistReceipt(partlyClaimed, ProductionReceiptStatus.PENDING_APPROVAL,
                new BigDecimal("2.000000"), fixture);

        assertThat(receiptCandidates(fixture)).extracting(WorkOrder::getWorkOrderId)
                .containsExactly(partlyClaimed.getWorkOrderId());
    }

    @Test
    void findReceiptCandidates_excludesStatusesThatCannotTakeAReceipt() {
        Fixture fixture = persistFixture();

        for (WorkOrderStatus refused : List.of(WorkOrderStatus.DRAFT, WorkOrderStatus.PLANNED,
                WorkOrderStatus.BLOCKED, WorkOrderStatus.CANCELLED)) {
            persistWorkOrder(fixture, "WO-" + refused, refused,
                    new BigDecimal("10.000000"), new BigDecimal("6.000000"));
        }
        WorkOrder eligible = persistWorkOrder(fixture, "WO-OK", WorkOrderStatus.RELEASED,
                new BigDecimal("10.000000"), new BigDecimal("6.000000"));

        assertThat(receiptCandidates(fixture)).extracting(WorkOrder::getWorkOrderId)
                .containsExactly(eligible.getWorkOrderId());
    }

    @Test
    void findReceiptCandidates_excludesAnotherPlant() {
        Fixture fixture = persistFixture();
        Fixture other = persistFixture();

        persistWorkOrder(other, "WO-FOREIGN", WorkOrderStatus.RELEASED,
                new BigDecimal("10.000000"), new BigDecimal("6.000000"));
        WorkOrder mine = persistWorkOrder(fixture, "WO-MINE", WorkOrderStatus.RELEASED,
                new BigDecimal("10.000000"), new BigDecimal("6.000000"));

        assertThat(receiptCandidates(fixture)).extracting(WorkOrder::getWorkOrderId)
                .containsExactly(mine.getWorkOrderId());
    }

    /**
     * Spec §10.3 names "cumulative good/receipt: atomic update" as the field concurrency must protect.
     * {@code @Version} on {@link com.erp.manufacturing.common.audit.BaseEntity} is what does that, and
     * nothing in the suite proved it actually applies to {@code work_orders} until F8.
     *
     * <p>Two detached copies of one row, both loaded at version 0: the first save wins, the second
     * must be rejected rather than silently overwriting the good quantity the first one recorded.
     */
    @Test
    void concurrentGoodQuantityUpdate_staleVersion_throwsOptimisticLockException() {
        Fixture fixture = persistFixture();
        WorkOrder persisted = persistWorkOrder(fixture, "WO-RACE", WorkOrderStatus.IN_PROGRESS,
                new BigDecimal("10.000000"), BigDecimal.ZERO);
        entityManager.clear();

        WorkOrder first = repository.findById(persisted.getWorkOrderId()).orElseThrow();
        entityManager.detach(first);
        WorkOrder second = repository.findById(persisted.getWorkOrderId()).orElseThrow();
        entityManager.detach(second);

        first.setActualGoodQuantity(new BigDecimal("4.000000"));
        repository.saveAndFlush(first);

        second.setActualGoodQuantity(new BigDecimal("7.000000"));
        assertThatThrownBy(() -> repository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void search_matchesTheWorkOrderNumber() {
        Fixture fixture = persistFixture();
        WorkOrder wanted = persistWorkOrder(fixture, "WO-ALPHA-1", WorkOrderStatus.DRAFT,
                BigDecimal.TEN, BigDecimal.ZERO);
        persistWorkOrder(fixture, "WO-BETA-1", WorkOrderStatus.DRAFT, BigDecimal.TEN, BigDecimal.ZERO);

        List<WorkOrder> found = repository
                .search(fixture.plant.getPlantId(), null, null, "alpha", PageRequest.of(0, 20))
                .getContent();

        assertThat(found).extracting(WorkOrder::getWorkOrderId).containsExactly(wanted.getWorkOrderId());
    }

    /** Spec §3.2 lists one {@code search} box; matching only the document number would half-answer it. */
    @Test
    void search_alsoMatchesTheProductSku() {
        Fixture fixture = persistFixture();
        WorkOrder wanted = persistWorkOrder(fixture, "WO-1", WorkOrderStatus.DRAFT,
                BigDecimal.TEN, BigDecimal.ZERO);
        persistWorkOrder(fixture, "WO-2", WorkOrderStatus.DRAFT, BigDecimal.TEN, BigDecimal.ZERO);

        List<WorkOrder> found = repository
                .search(fixture.plant.getPlantId(), null, null,
                        fixture.product.getCode().substring(0, 12), PageRequest.of(0, 20))
                .getContent();

        // Both work orders share the fixture's product, so an SKU match returns both.
        assertThat(found).extracting(WorkOrder::getWorkOrderId)
                .contains(wanted.getWorkOrderId())
                .hasSize(2);
    }

    @Test
    void search_withoutATermReturnsEveryWorkOrderOfThePlant() {
        Fixture fixture = persistFixture();
        persistWorkOrder(fixture, "WO-1", WorkOrderStatus.DRAFT, BigDecimal.TEN, BigDecimal.ZERO);
        persistWorkOrder(fixture, "WO-2", WorkOrderStatus.COMPLETED, BigDecimal.TEN, BigDecimal.TEN);

        assertThat(repository.search(fixture.plant.getPlantId(), null, null, null, PageRequest.of(0, 20)))
                .hasSize(2);
    }

    private List<WorkOrder> receiptCandidates(Fixture fixture) {
        return repository.findReceiptCandidates(
                        fixture.plant.getPlantId(), RECEIPT_CANDIDATE_STATUSES, PageRequest.of(0, 20))
                .getContent();
    }

    /** One receipt with a single line, which is the shape F5 collapsed the document to. */
    private void persistReceipt(WorkOrder workOrder,
                                ProductionReceiptStatus status,
                                BigDecimal quantity,
                                Fixture fixture) {
        Instant now = Instant.now();
        ProductionReceipt receipt = ProductionReceipt.builder()
                .workOrder(workOrder)
                .status(status)
                .code("PR-" + UUID.randomUUID().toString().substring(0, 8))
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        receipt.setCreatedAt(now);
        receipt.setUpdatedAt(now);

        ProductionReceiptLine line = ProductionReceiptLine.builder()
                .receipt(receipt)
                .item(fixture.product)
                .warehouse(fixture.warehouse)
                .quantity(quantity)
                .build();
        line.setCreatedAt(now);
        line.setUpdatedAt(now);
        receipt.getLines().add(line);

        entityManager.persistAndFlush(receipt);
    }

    // @DataJpaTest does not load JpaAuditingConfig/SecurityAuditorAware, so createdAt/updatedAt
    // (NOT NULL on BaseEntity subclasses) are set explicitly before persisting.

    private record Fixture(Plant plant, Warehouse warehouse, Item product, BomHeader bom) {}

    private Fixture persistFixture() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Company company = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        company = entityManager.persistFlushFind(company);

        Plant plant = Plant.builder().company(company).code("PL_" + suffix).name("Plant " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        plant.setCreatedAt(now);
        plant.setUpdatedAt(now);
        plant = entityManager.persistFlushFind(plant);

        Warehouse warehouse = Warehouse.builder().plant(plant).code("WH_" + suffix)
                .name("Warehouse " + suffix).type(WarehouseType.FINISHED_GOODS)
                .status(OrganizationStatus.ACTIVE).build();
        warehouse.setCreatedAt(now);
        warehouse.setUpdatedAt(now);
        warehouse = entityManager.persistFlushFind(warehouse);

        Item product = Item.builder().company(company).code("SKU_" + suffix).name("Product " + suffix)
                .type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).build();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        product = entityManager.persistFlushFind(product);

        BomHeader bom = BomHeader.builder().company(company).parentItem(product).revision("R1")
                .status(BomStatus.ACTIVE).build();
        bom.setCreatedAt(now);
        bom.setUpdatedAt(now);
        bom = entityManager.persistFlushFind(bom);

        return new Fixture(plant, warehouse, product, bom);
    }

    private WorkOrder persistWorkOrder(Fixture fixture,
                                       String workOrderNo,
                                       WorkOrderStatus status,
                                       BigDecimal plannedQuantity,
                                       BigDecimal actualGoodQuantity) {
        Instant now = Instant.now();
        WorkOrder workOrder = WorkOrder.builder()
                .company(fixture.plant.getCompany())
                .plant(fixture.plant)
                .workOrderNo(workOrderNo + "-" + UUID.randomUUID())
                .productItem(fixture.product)
                .bom(fixture.bom)
                .bomRevision("R1")
                .outputWarehouse(fixture.warehouse)
                .plannedQuantity(plannedQuantity)
                .completedQuantity(BigDecimal.ZERO)
                .actualGoodQuantity(actualGoodQuantity)
                .actualScrapQuantity(BigDecimal.ZERO)
                .actualReworkQuantity(BigDecimal.ZERO)
                .status(status)
                .build();
        workOrder.setCreatedAt(now);
        workOrder.setUpdatedAt(now);
        return entityManager.persistFlushFind(workOrder);
    }
}
