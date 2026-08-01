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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two production-receipt queries F8 adds, against a real Postgres Testcontainer.
 *
 * <p>Both are JPQL and therefore untestable with a mocked repository (rule R7):
 * <ul>
 *   <li>{@code findByPlant} (spec §6.2) — the flat plant-scoped list, including its optional status
 *       filter and its plant isolation;</li>
 *   <li>{@code sumOpenQuantityByWorkOrderIds} — the batch form of the B16 ceiling. Getting the status
 *       set wrong here understates or overstates what a receipt may claim, and no unit test can see
 *       it because the {@code in (...)} list lives inside the query string.</li>
 * </ul>
 *
 * <p>Following the convention of {@link WorkOrderRepositoryIT}, every exclusion case also seeds a row
 * that must come back, so an empty result caused by a broken fixture cannot masquerade as a correct
 * exclusion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductionReceiptRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    ProductionReceiptRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void findByPlant_withoutAStatusFilter_returnsEveryReceiptOfThePlant() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);

        ProductionReceipt draft = persistReceipt(workOrder, fixture,
                ProductionReceiptStatus.DRAFT, new BigDecimal("2.000000"));
        ProductionReceipt approved = persistReceipt(workOrder, fixture,
                ProductionReceiptStatus.APPROVED, new BigDecimal("3.000000"));

        assertThat(repository.findByPlant(fixture.plant.getPlantId(), null, PageRequest.of(0, 20)))
                .extracting(ProductionReceipt::getReceiptId)
                .containsExactlyInAnyOrder(draft.getReceiptId(), approved.getReceiptId());
    }

    @Test
    void findByPlant_withAStatusFilter_returnsOnlyThatStatus() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);

        ProductionReceipt draft = persistReceipt(workOrder, fixture,
                ProductionReceiptStatus.DRAFT, new BigDecimal("2.000000"));
        persistReceipt(workOrder, fixture, ProductionReceiptStatus.APPROVED, new BigDecimal("3.000000"));

        assertThat(repository.findByPlant(
                fixture.plant.getPlantId(), ProductionReceiptStatus.DRAFT, PageRequest.of(0, 20)))
                .extracting(ProductionReceipt::getReceiptId)
                .containsExactly(draft.getReceiptId());
    }

    /** The receipt has no plant of its own — it inherits one through its work order. */
    @Test
    void findByPlant_excludesReceiptsOfAnotherPlant() {
        Fixture fixture = persistFixture();
        Fixture other = persistFixture();

        ProductionReceipt mine = persistReceipt(
                persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS), fixture,
                ProductionReceiptStatus.DRAFT, new BigDecimal("2.000000"));
        persistReceipt(persistWorkOrder(other, WorkOrderStatus.IN_PROGRESS), other,
                ProductionReceiptStatus.DRAFT, new BigDecimal("5.000000"));

        assertThat(repository.findByPlant(fixture.plant.getPlantId(), null, PageRequest.of(0, 20)))
                .extracting(ProductionReceipt::getReceiptId)
                .containsExactly(mine.getReceiptId());
    }

    /**
     * Invariant B16 in batch form: only receipts that are still <em>open</em> have claimed output.
     * {@code APPROVED} must not count — approval already moved that quantity into
     * {@code completedQuantity}, so counting it here would deduct it twice and hide output that
     * genuinely still has to be warehoused. {@code REJECTED} claims nothing at all.
     */
    @Test
    void sumOpenQuantityByWorkOrderIds_countsDraftAndPendingOnly() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);

        persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("2.000000"));
        persistReceipt(workOrder, fixture, ProductionReceiptStatus.PENDING_APPROVAL, new BigDecimal("3.000000"));
        persistReceipt(workOrder, fixture, ProductionReceiptStatus.APPROVED, new BigDecimal("4.000000"));
        persistReceipt(workOrder, fixture, ProductionReceiptStatus.REJECTED, new BigDecimal("5.000000"));

        assertThat(openQuantities(workOrder))
                .containsExactly(Map.entry(workOrder.getWorkOrderId(), new BigDecimal("5.000000")));
    }

    @Test
    void sumOpenQuantityByWorkOrderIds_groupsPerWorkOrder() {
        Fixture fixture = persistFixture();
        WorkOrder first = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        WorkOrder second = persistWorkOrder(fixture, WorkOrderStatus.RELEASED);

        persistReceipt(first, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("2.000000"));
        persistReceipt(second, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("7.000000"));

        assertThat(openQuantities(first, second)).containsOnly(
                Map.entry(first.getWorkOrderId(), new BigDecimal("2.000000")),
                Map.entry(second.getWorkOrderId(), new BigDecimal("7.000000")));
    }

    /**
     * A {@code group by} returns no row at all for a work order with nothing open — it does not return
     * zero. Callers therefore have to default, and this pins that they must.
     */
    @Test
    void sumOpenQuantityByWorkOrderIds_omitsWorkOrdersWithNothingOpen() {
        Fixture fixture = persistFixture();
        WorkOrder withOpenReceipt = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        WorkOrder withNone = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);

        persistReceipt(withOpenReceipt, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("2.000000"));

        assertThat(openQuantities(withOpenReceipt, withNone))
                .containsOnlyKeys(withOpenReceipt.getWorkOrderId());
    }

    private Map<UUID, BigDecimal> openQuantities(WorkOrder... workOrders) {
        List<UUID> ids = java.util.Arrays.stream(workOrders).map(WorkOrder::getWorkOrderId).toList();
        return repository.sumOpenQuantityByWorkOrderIds(ids).stream()
                .collect(Collectors.toMap(
                        OpenReceiptQuantityProjection::getWorkOrderId,
                        OpenReceiptQuantityProjection::getOpenQuantity));
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

    private WorkOrder persistWorkOrder(Fixture fixture, WorkOrderStatus status) {
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
                .actualGoodQuantity(new BigDecimal("50.000000"))
                .actualScrapQuantity(BigDecimal.ZERO)
                .actualReworkQuantity(BigDecimal.ZERO)
                .status(status)
                .build();
        workOrder.setCreatedAt(now);
        workOrder.setUpdatedAt(now);
        return entityManager.persistFlushFind(workOrder);
    }

    private ProductionReceipt persistReceipt(WorkOrder workOrder,
                                             Fixture fixture,
                                             ProductionReceiptStatus status,
                                             BigDecimal quantity) {
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

        return entityManager.persistFlushFind(receipt);
    }
}
