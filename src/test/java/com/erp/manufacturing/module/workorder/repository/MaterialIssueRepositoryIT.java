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
import com.erp.manufacturing.module.workorder.domain.MaterialIssue;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code MaterialIssueRepository.findByPlant} (spec §4.1) against a real Postgres Testcontainer.
 *
 * <p>The predicate reaches the plant <em>through</em> the work order and makes {@code workOrderId}
 * optional with a {@code :param is null} guard — both are JPQL-only behaviour that a mocked
 * repository cannot exercise (rule R7). The optional-filter idiom in particular is easy to get
 * backwards in a way that silently returns everything.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MaterialIssueRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    MaterialIssueRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void findByPlant_withoutAWorkOrderFilter_returnsEveryIssueOfThePlant() {
        Fixture fixture = persistFixture();
        MaterialIssue first = persistIssue(persistWorkOrder(fixture));
        MaterialIssue second = persistIssue(persistWorkOrder(fixture));

        assertThat(repository.findByPlant(fixture.plant.getPlantId(), null, null, PageRequest.of(0, 20)))
                .extracting(MaterialIssue::getIssueId)
                .containsExactlyInAnyOrder(first.getIssueId(), second.getIssueId());
    }

    @Test
    void findByPlant_withAWorkOrderFilter_returnsOnlyThatWorkOrder() {
        Fixture fixture = persistFixture();
        WorkOrder wanted = persistWorkOrder(fixture);
        MaterialIssue mine = persistIssue(wanted);
        persistIssue(persistWorkOrder(fixture));

        assertThat(repository.findByPlant(
                fixture.plant.getPlantId(), wanted.getWorkOrderId(), null, PageRequest.of(0, 20)))
                .extracting(MaterialIssue::getIssueId)
                .containsExactly(mine.getIssueId());
    }

    /** The issue has no plant column — it inherits one through its work order. */
    @Test
    void findByPlant_excludesIssuesOfAnotherPlant() {
        Fixture fixture = persistFixture();
        Fixture other = persistFixture();

        MaterialIssue mine = persistIssue(persistWorkOrder(fixture));
        persistIssue(persistWorkOrder(other));

        assertThat(repository.findByPlant(fixture.plant.getPlantId(), null, null, PageRequest.of(0, 20)))
                .extracting(MaterialIssue::getIssueId)
                .containsExactly(mine.getIssueId());
    }

    /**
     * A work order of a <em>different</em> plant must not be reachable by naming its id: the plant
     * predicate has to keep applying, not be superseded by the narrowing filter.
     */
    @Test
    void findByPlant_workOrderOfAnotherPlant_returnsNothing() {
        Fixture fixture = persistFixture();
        Fixture other = persistFixture();

        WorkOrder foreign = persistWorkOrder(other);
        persistIssue(foreign);
        persistIssue(persistWorkOrder(fixture));

        assertThat(repository.findByPlant(
                fixture.plant.getPlantId(), foreign.getWorkOrderId(), null, PageRequest.of(0, 20)))
                .isEmpty();
    }

    /**
     * F10 / debt H. {@code MaterialIssue.assignCode()} runs as {@code @PrePersist}, and only a real
     * insert proves it: Hibernate snapshots the entity when it queues the INSERT, so a code assigned
     * after {@code save()} would simply not be in the statement and the NOT NULL column would blow
     * up (CLAUDE.md §0.12 #5). A mocked repository never fires the callback at all.
     *
     * <p>The expected value is written out literally rather than by calling {@code assignCode()}:
     * asserting against the code under test is the tautology rule D7b.3 warns about.
     */
    @Test
    void persist_withoutACode_derivesTheDocumentNumberFromTheId() {
        Fixture fixture = persistFixture();

        MaterialIssue issue = persistIssue(persistWorkOrder(fixture));

        String expected = "MI-" + issue.getIssueId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        assertThat(issue.getCode()).isEqualTo(expected);
        assertThat(issue.getCode()).startsWith("MI-").hasSize(11);
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
                .name("Warehouse " + suffix).type(WarehouseType.RAW_MATERIAL)
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

    private WorkOrder persistWorkOrder(Fixture fixture) {
        Instant now = Instant.now();
        WorkOrder workOrder = WorkOrder.builder()
                .company(fixture.plant.getCompany())
                .plant(fixture.plant)
                .workOrderNo("WO-" + UUID.randomUUID())
                .productItem(fixture.product)
                .bom(fixture.bom)
                .bomRevision("R1")
                .outputWarehouse(fixture.warehouse)
                .plannedQuantity(new BigDecimal("100.000000"))
                .completedQuantity(BigDecimal.ZERO)
                .actualGoodQuantity(BigDecimal.ZERO)
                .actualScrapQuantity(BigDecimal.ZERO)
                .actualReworkQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.IN_PROGRESS)
                .build();
        workOrder.setCreatedAt(now);
        workOrder.setUpdatedAt(now);
        return entityManager.persistFlushFind(workOrder);
    }

    /**
     * The Over-BOM approval queue is a server-side filter, so the predicate has to work in the
     * database, not in the caller. A page of POSTED history that happens to contain no pending row
     * would look identical to a correctly filtered empty queue, which is why both directions and the
     * unfiltered baseline are asserted together.
     */
    @Test
    void findByPlant_withAStatusFilter_returnsOnlyThatStatus() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        MaterialIssue posted = persistIssue(workOrder);
        MaterialIssue pending = persistIssue(workOrder, MaterialIssueStatus.PENDING_APPROVAL);
        persistIssue(workOrder, MaterialIssueStatus.REJECTED);

        UUID plantId = fixture.plant.getPlantId();
        assertThat(repository.findByPlant(plantId, null, MaterialIssueStatus.PENDING_APPROVAL,
                PageRequest.of(0, 20)))
                .extracting(MaterialIssue::getIssueId)
                .containsExactly(pending.getIssueId());
        assertThat(repository.findByPlant(plantId, null, MaterialIssueStatus.POSTED, PageRequest.of(0, 20)))
                .extracting(MaterialIssue::getIssueId)
                .containsExactly(posted.getIssueId());
        assertThat(repository.findByPlant(plantId, null, null, PageRequest.of(0, 20)))
                .hasSize(3);
    }

    /** The status filter must not override the plant predicate it is combined with. */
    @Test
    void findByPlant_withAStatusFilter_stillExcludesAnotherPlant() {
        Fixture fixture = persistFixture();
        Fixture other = persistFixture();
        MaterialIssue mine = persistIssue(persistWorkOrder(fixture), MaterialIssueStatus.PENDING_APPROVAL);
        persistIssue(persistWorkOrder(other), MaterialIssueStatus.PENDING_APPROVAL);

        assertThat(repository.findByPlant(fixture.plant.getPlantId(), null,
                MaterialIssueStatus.PENDING_APPROVAL, PageRequest.of(0, 20)))
                .extracting(MaterialIssue::getIssueId)
                .containsExactly(mine.getIssueId());
    }

    @Test
    void findByWorkOrder_filtersByStatusAndStaysInsideTheWorkOrder() {
        Fixture fixture = persistFixture();
        WorkOrder mine = persistWorkOrder(fixture);
        WorkOrder other = persistWorkOrder(fixture);
        MaterialIssue pending = persistIssue(mine, MaterialIssueStatus.PENDING_APPROVAL);
        persistIssue(mine);
        persistIssue(other, MaterialIssueStatus.PENDING_APPROVAL);

        assertThat(repository.findByWorkOrder(mine.getWorkOrderId(), MaterialIssueStatus.PENDING_APPROVAL,
                PageRequest.of(0, 20)))
                .extracting(MaterialIssue::getIssueId)
                .containsExactly(pending.getIssueId());
        assertThat(repository.findByWorkOrder(mine.getWorkOrderId(), null, PageRequest.of(0, 20)))
                .hasSize(2);
    }

    private MaterialIssue persistIssue(WorkOrder workOrder, MaterialIssueStatus status) {
        Instant now = Instant.now();
        MaterialIssue issue = MaterialIssue.builder()
                .workOrder(workOrder)
                .status(status)
                .idempotencyKey(UUID.randomUUID().toString())
                // Only a POSTED document has a postedAt — that is exactly why the approval queue
                // cannot be sorted by it.
                .postedAt(status == MaterialIssueStatus.POSTED ? now : null)
                .requestedAt(now)
                .build();
        issue.setCreatedAt(now);
        issue.setUpdatedAt(now);
        return entityManager.persistFlushFind(issue);
    }

    private MaterialIssue persistIssue(WorkOrder workOrder) {
        Instant now = Instant.now();
        MaterialIssue issue = MaterialIssue.builder()
                .workOrder(workOrder)
                .status(MaterialIssueStatus.POSTED)
                .idempotencyKey(UUID.randomUUID().toString())
                .postedAt(now)
                .build();
        issue.setCreatedAt(now);
        issue.setUpdatedAt(now);
        return entityManager.persistFlushFind(issue);
    }
}
