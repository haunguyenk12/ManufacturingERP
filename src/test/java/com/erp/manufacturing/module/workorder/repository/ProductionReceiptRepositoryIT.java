package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.ProductionReceipt;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptContribution;
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

    @Autowired
    ProductionReceiptContributionRepository contributionRepository;

    @Autowired
    ProductionReceiptLineRepository lineRepository;

    @Test
    void findByPlant_withoutAStatusFilter_returnsEveryReceiptOfThePlant() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);

        ProductionReceipt draft = persistReceipt(workOrder, fixture,
                ProductionReceiptStatus.DRAFT, new BigDecimal("2.000000"));
        ProductionReceipt approved = persistReceipt(workOrder, fixture,
                ProductionReceiptStatus.APPROVED, new BigDecimal("3.000000"));

        assertThat(repository.findByPlant(fixture.plant.getPlantId(), null, null, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
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
                fixture.plant.getPlantId(), ProductionReceiptStatus.DRAFT, null, null, null, null, null, null, null, null,
                PageRequest.of(0, 20)))
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

        assertThat(repository.findByPlant(fixture.plant.getPlantId(), null, null, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
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

    // ---------------------------------------------------------------------------------------
    // G9a filters (FE §3.3) — JPQL only, so only a real database can test them (R7)
    // ---------------------------------------------------------------------------------------

    private List<UUID> plantList(Fixture fixture, String search, java.time.LocalDate from, java.time.LocalDate to,
                                 UUID warehouseId) {
        return repository.findByPlant(fixture.plant.getPlantId(), null, null, warehouseId, from, to, search, null, null, null,
                        PageRequest.of(0, 20))
                .map(ProductionReceipt::getReceiptId).getContent();
    }

    /** 🔴 With every filter null the query still runs — the lower(bytea) trap of §0.24 on the new search. */
    @Test
    void findByPlant_searchesTheLotNumberAndTheReceiptCodeIgnoringCase() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        ProductionReceipt withLot = persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("1"));
        withLot.getLines().get(0).setRequestedLotCode("GD9-FG-01");
        entityManager.persistAndFlush(withLot.getLines().get(0));
        ProductionReceipt other = persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("1"));

        assertThat(plantList(fixture, "gd9-fg", null, null, null)).containsExactly(withLot.getReceiptId());
        assertThat(plantList(fixture, other.getCode().toLowerCase(), null, null, null))
                .containsExactly(other.getReceiptId());
        assertThat(plantList(fixture, null, null, null, null))
                .containsExactlyInAnyOrder(withLot.getReceiptId(), other.getReceiptId());
    }

    @Test
    void findByPlant_receivedOnRangeIsInclusiveOnBothEnds() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        ProductionReceipt first = persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("1"));
        ProductionReceipt later = persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("1"));
        later.setReceivedOn(java.time.LocalDate.of(2026, 10, 5));
        entityManager.persistAndFlush(later);

        assertThat(plantList(fixture, null, java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 1), null))
                .containsExactly(first.getReceiptId());
        assertThat(plantList(fixture, null, java.time.LocalDate.of(2026, 10, 2), java.time.LocalDate.of(2026, 10, 5), null))
                .containsExactly(later.getReceiptId());
    }

    @Test
    void findByPlant_filtersByDestinationWarehouse() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        ProductionReceipt receipt = persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("1"));

        assertThat(plantList(fixture, null, null, null, fixture.warehouse.getWarehouseId()))
                .containsExactly(receipt.getReceiptId());
        assertThat(plantList(fixture, null, null, null, UUID.randomUUID())).isEmpty();
    }


    // ---------------------------------------------------------------------------------------
    // G9b — contribution ledger queries (V84)
    // ---------------------------------------------------------------------------------------

    /** Only receipts that still claim output count, and an edited draft can leave itself out. */
    @Test
    void sumLiveByExecution_countsLiveReceiptsAndCanExcludeOne() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        ProductionExecution execution = persistExecution(workOrder, "8");
        ProductionReceipt draft = persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("2"));
        ProductionReceipt approved = persistReceipt(workOrder, fixture, ProductionReceiptStatus.APPROVED, new BigDecimal("3"));
        ProductionReceipt rejected = persistReceipt(workOrder, fixture, ProductionReceiptStatus.REJECTED, new BigDecimal("4"));
        persistContribution(draft, execution, "2");
        persistContribution(approved, execution, "3");
        persistContribution(rejected, execution, "4");

        assertThat(claimedBy(contributionRepository.sumLiveByExecution(workOrder.getWorkOrderId(), null)))
                .containsOnlyKeys(execution.getProductionExecutionId())
                .hasEntrySatisfying(execution.getProductionExecutionId(), sum -> assertThat(sum).isEqualByComparingTo("5"));
        assertThat(claimedBy(contributionRepository.sumLiveByExecution(workOrder.getWorkOrderId(), draft.getReceiptId())))
                .hasEntrySatisfying(execution.getProductionExecutionId(), sum -> assertThat(sum).isEqualByComparingTo("3"));
    }

    @Test
    void sumLiveByExecution_ignoresAnotherWorkOrder() {
        Fixture fixture = persistFixture();
        WorkOrder mine = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        WorkOrder other = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        ProductionExecution mineExecution = persistExecution(mine, "5");
        ProductionExecution otherExecution = persistExecution(other, "5");
        persistContribution(persistReceipt(mine, fixture, ProductionReceiptStatus.DRAFT, BigDecimal.ONE), mineExecution, "1");
        persistContribution(persistReceipt(other, fixture, ProductionReceiptStatus.DRAFT, BigDecimal.ONE), otherExecution, "1");

        assertThat(claimedBy(contributionRepository.sumLiveByExecution(mine.getWorkOrderId(), null)))
                .containsOnlyKeys(mineExecution.getProductionExecutionId());
    }

    /** B231: a live receipt with no contribution row is a pre-V84 receipt; one with rows or a dead one is not. */
    @Test
    void sumLiveWithoutContributions_sumsLiveReceiptsThatHaveNoRow() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        ProductionExecution execution = persistExecution(workOrder, "9");
        ProductionReceipt legacyApproved = persistReceipt(workOrder, fixture, ProductionReceiptStatus.APPROVED, new BigDecimal("3"));
        persistReceipt(workOrder, fixture, ProductionReceiptStatus.REJECTED, new BigDecimal("5"));
        ProductionReceipt recorded = persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("2"));
        persistContribution(recorded, execution, "2");

        assertThat(repository.sumLiveWithoutContributions(workOrder.getWorkOrderId(), null)).isEqualByComparingTo("3");
        assertThat(repository.sumLiveWithoutContributions(workOrder.getWorkOrderId(), legacyApproved.getReceiptId()))
                .isEqualByComparingTo("0");
    }

    @Test
    void deleteByReceiptId_removesOnlyThatReceiptsRows() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        ProductionExecution execution = persistExecution(workOrder, "9");
        ProductionReceipt edited = persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("2"));
        ProductionReceipt kept = persistReceipt(workOrder, fixture, ProductionReceiptStatus.DRAFT, new BigDecimal("3"));
        persistContribution(edited, execution, "2");
        persistContribution(kept, execution, "3");

        assertThat(contributionRepository.deleteByReceiptId(edited.getReceiptId())).isEqualTo(1);

        assertThat(contributionRepository.findByReceiptReceiptIdIn(List.of(edited.getReceiptId(), kept.getReceiptId())))
                .extracting(contribution -> contribution.getReceipt().getReceiptId())
                .containsExactly(kept.getReceiptId());
    }

    private static Map<UUID, BigDecimal> claimedBy(List<KeyedQuantityProjection> rows) {
        return rows.stream().collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
    }

    private ProductionExecution persistExecution(WorkOrder workOrder, String good) {
        Instant now = Instant.now();
        ProductionExecution execution = ProductionExecution.builder()
                .workOrder(workOrder)
                .goodQuantity(new BigDecimal(good))
                .actualStartedAt(now.minusSeconds(3600))
                .actualEndedAt(now)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        execution.setCreatedAt(now);
        execution.setUpdatedAt(now);
        return entityManager.persistFlushFind(execution);
    }

    private void persistContribution(ProductionReceipt receipt, ProductionExecution execution, String quantity) {
        Instant now = Instant.now();
        ProductionReceiptContribution contribution = ProductionReceiptContribution.builder()
                .receipt(receipt)
                .execution(execution)
                .quantity(new BigDecimal(quantity))
                .build();
        contribution.setCreatedAt(now);
        contribution.setUpdatedAt(now);
        entityManager.persistAndFlush(contribution);
    }


    // ---------------------------------------------------------------------------------------
    // G9c — quality filters and the HOLD gate (V85)
    // ---------------------------------------------------------------------------------------

    private ProductionReceipt judged(WorkOrder workOrder, Fixture fixture, ProductionReceiptStatus status,
                                     String quantity, String accepted, String rejected) {
        ProductionReceipt receipt = persistReceipt(workOrder, fixture, status, new BigDecimal(quantity));
        receipt.getLines().get(0).recordQualityDecision(new BigDecimal(accepted), new BigDecimal(rejected));
        entityManager.flush();
        return receipt;
    }

    private List<UUID> byQuality(Fixture fixture, String state, Boolean pendingQc, Boolean hasQcDecision) {
        return repository.findByPlant(fixture.plant.getPlantId(), null, null, null, null, null, null,
                        state, pendingQc, hasQcDecision, PageRequest.of(0, 20))
                .map(ProductionReceipt::getReceiptId).getContent();
    }

    /** FE Q20/Q21: each quality state, pendingQc and hasQcDecision follow the same rule as ProductionReceipt. */
    @Test
    void findByPlant_qualityFiltersFollowTheLineTotals() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        UUID notStarted = judged(workOrder, fixture, ProductionReceiptStatus.APPROVED, "5", "0", "0").getReceiptId();
        UUID partial = judged(workOrder, fixture, ProductionReceiptStatus.APPROVED, "5", "2", "1").getReceiptId();
        UUID accepted = judged(workOrder, fixture, ProductionReceiptStatus.APPROVED, "5", "5", "0").getReceiptId();
        UUID rejected = judged(workOrder, fixture, ProductionReceiptStatus.APPROVED, "5", "0", "5").getReceiptId();
        UUID mixed = judged(workOrder, fixture, ProductionReceiptStatus.APPROVED, "5", "3", "2").getReceiptId();
        UUID draft = judged(workOrder, fixture, ProductionReceiptStatus.DRAFT, "5", "0", "0").getReceiptId();

        assertThat(byQuality(fixture, "NOT_STARTED", null, null)).containsExactly(notStarted);
        assertThat(byQuality(fixture, "PARTIAL", null, null)).containsExactly(partial);
        assertThat(byQuality(fixture, "ACCEPTED", null, null)).containsExactly(accepted);
        assertThat(byQuality(fixture, "REJECTED", null, null)).containsExactly(rejected);
        assertThat(byQuality(fixture, "MIXED", null, null)).containsExactly(mixed);
        assertThat(byQuality(fixture, null, true, null)).containsExactlyInAnyOrder(notStarted, partial);
        assertThat(byQuality(fixture, null, false, null)).containsExactlyInAnyOrder(accepted, rejected, mixed, draft);
        // Q21: the history keeps receipts judged only in part — pendingQc=false would lose them.
        assertThat(byQuality(fixture, null, null, true)).containsExactlyInAnyOrder(partial, accepted, rejected, mixed);
        assertThat(byQuality(fixture, null, null, false)).containsExactlyInAnyOrder(notStarted, draft);
        assertThat(byQuality(fixture, null, null, null)).hasSize(6);
    }

    /** C2-2 / G9c: the lot of an approved line with QC pending is gated; once judged it is free. */
    @Test
    void existsPendingQualityForLot_onlyWhileAnApprovedLineHasSomethingPending() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        InventoryLot pendingLot = persistLot(fixture, "G9C-PEND");
        InventoryLot judgedLot = persistLot(fixture, "G9C-DONE");
        InventoryLot rejectedReceiptLot = persistLot(fixture, "G9C-REJ");
        attachLot(judged(workOrder, fixture, ProductionReceiptStatus.APPROVED, "5", "2", "1"), pendingLot);
        attachLot(judged(workOrder, fixture, ProductionReceiptStatus.APPROVED, "5", "3", "2"), judgedLot);
        attachLot(judged(workOrder, fixture, ProductionReceiptStatus.REJECTED, "5", "0", "0"), rejectedReceiptLot);

        assertThat(lineRepository.existsPendingQualityForLot(pendingLot.getLotId(), ProductionReceiptStatus.APPROVED)).isTrue();
        assertThat(lineRepository.existsPendingQualityForLot(judgedLot.getLotId(), ProductionReceiptStatus.APPROVED)).isFalse();
        assertThat(lineRepository.existsPendingQualityForLot(rejectedReceiptLot.getLotId(), ProductionReceiptStatus.APPROVED))
                .isFalse();
    }

    /** V85: a line can never be judged past its quantity. */
    @Test
    void lineTotals_cannotExceedTheReceivedQuantity() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        ProductionReceipt receipt = persistReceipt(workOrder, fixture, ProductionReceiptStatus.APPROVED, new BigDecimal("5"));
        receipt.getLines().get(0).recordQualityDecision(new BigDecimal("4"), new BigDecimal("2"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> entityManager.flush())
                .hasMessageContaining("chk_production_receipt_lines_qc_within_quantity");
    }

    private InventoryLot persistLot(Fixture fixture, String code) {
        InventoryLot lot = InventoryLot.builder()
                .item(fixture.product)
                .lotCode(code + "-" + UUID.randomUUID().toString().substring(0, 6))
                .status(LotStatus.HOLD)
                .receivedAt(Instant.now())
                .build();
        lot.setCreatedAt(Instant.now());
        lot.setUpdatedAt(Instant.now());
        return entityManager.persistFlushFind(lot);
    }

    private void attachLot(ProductionReceipt receipt, InventoryLot lot) {
        receipt.getLines().get(0).setLot(lot);
        entityManager.flush();
    }

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
                .bomCode("BOM-R1").versionNumber(1)
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
                .receivedOn(java.time.LocalDate.of(2026, 10, 1))
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
