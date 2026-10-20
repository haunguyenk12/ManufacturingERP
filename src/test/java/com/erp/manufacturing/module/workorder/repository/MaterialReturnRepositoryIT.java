package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.MovementDirection;
import com.erp.manufacturing.module.inventory.domain.MovementType;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.workorder.domain.MaterialIssue;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueLine;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.MaterialReturn;
import com.erp.manufacturing.module.workorder.domain.MaterialReturnLine;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G8d: the return ledger, the posted issue lines of a work order, the WIP list and the rework totals per work order are
 * JPQL. A mocked repository cannot tell a missing filter, an inner join on a nullable lot or an untyped null parameter
 * from a right query (rule R7, CLAUDE.md §0.24), so these run against Postgres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MaterialReturnRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired MaterialReturnRepository returnRepository;
    @Autowired MaterialReturnLineRepository returnLineRepository;
    @Autowired MaterialIssueLineRepository issueLineRepository;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired ProductionExecutionRepository executionRepository;
    @Autowired TestEntityManager entityManager;

    /** B222: returns are summed per source issue line; the lines of a page come back with source, lot (nullable), movement. */
    @Test
    void returnLines_areSummedPerSource_andComeBackWithTheirSource() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        WorkOrderComponentLine componentLine = persistComponentLine(workOrder, fixture);
        InventoryLot lot = persistLot(fixture);
        MaterialIssueLine withLot = persistIssueLine(workOrder, componentLine, fixture, MaterialIssueStatus.POSTED, lot, "8");
        MaterialIssueLine plain = persistIssueLine(workOrder, componentLine, fixture, MaterialIssueStatus.POSTED, null, "5");
        MaterialReturn first = persistReturn(workOrder, Instant.now());
        persistReturnLine(first, 1, withLot, fixture, "2");
        persistReturnLine(first, 2, plain, fixture, "1");
        MaterialReturn second = persistReturn(workOrder, Instant.now());
        persistReturnLine(second, 1, withLot, fixture, "0.5");
        entityManager.clear();

        Map<UUID, BigDecimal> returned = returnLineRepository.sumBySourceIssueLineIds(
                        List.of(withLot.getIssueLineId(), plain.getIssueLineId())).stream()
                .collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
        List<MaterialReturnLine> lines = returnLineRepository.findWithSourceByMaterialReturnIds(List.of(first.getMaterialReturnId()));

        assertThat(returned.get(withLot.getIssueLineId())).isEqualByComparingTo("2.5");
        assertThat(returned.get(plain.getIssueLineId())).isEqualByComparingTo("1");
        assertThat(lines).extracting(MaterialReturnLine::getLineNo).containsExactly(1, 2);
        assertThat(lines.get(0).getSourceIssueLine().getLot().getLotCode()).isEqualTo(lot.getLotCode());
        assertThat(lines.get(1).getSourceIssueLine().getLot()).isNull();
        assertThat(lines.get(1).getStockMovement().getMovementId()).isNotNull();
    }

    /** The plant list without a search or dates must not die on an untyped null (CLAUDE.md §0.24, §0.36 #4). */
    @Test
    void findByPlant_filtersBySearchAndAHalfOpenPostedRange_andStaysOnThePlant() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MaterialReturn early = persistReturn(workOrder, now.minus(2, ChronoUnit.DAYS));
        MaterialReturn late = persistReturn(workOrder, now);
        persistReturn(persistWorkOrder(persistFixture(), WorkOrderStatus.IN_PROGRESS), now);
        UUID plantId = fixture.plant().getPlantId();

        Page<MaterialReturn> all = returnRepository.findByPlant(plantId, null, null, null, null, PageRequest.of(0, 20));
        Page<MaterialReturn> byCode = returnRepository.findByPlant(plantId, null, late.getCode().toLowerCase(), null, null,
                PageRequest.of(0, 20));
        Page<MaterialReturn> byWorkOrder = returnRepository.findByPlant(plantId, null, workOrder.getWorkOrderNo(), null, null,
                PageRequest.of(0, 20));
        Page<MaterialReturn> range = returnRepository.findByPlant(plantId, workOrder.getWorkOrderId(), null,
                now.minus(1, ChronoUnit.DAYS), now, PageRequest.of(0, 20));

        assertThat(all.getContent()).extracting(MaterialReturn::getMaterialReturnId)
                .containsExactlyInAnyOrder(early.getMaterialReturnId(), late.getMaterialReturnId());
        assertThat(byCode.getContent()).extracting(MaterialReturn::getMaterialReturnId).containsExactly(late.getMaterialReturnId());
        assertThat(byWorkOrder.getTotalElements()).isEqualTo(2);
        assertThat(range.getContent()).isEmpty();
    }

    /** Only POSTED lines of this work order, lot or not: a pending request has put no material at the line yet. */
    @Test
    void findPostedByWorkOrder_keepsPostedLinesOfTheWorkOrderWithOrWithoutALot() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        WorkOrderComponentLine componentLine = persistComponentLine(workOrder, fixture);
        MaterialIssueLine posted = persistIssueLine(workOrder, componentLine, fixture, MaterialIssueStatus.POSTED, null, "8");
        MaterialIssueLine postedLot = persistIssueLine(workOrder, componentLine, fixture, MaterialIssueStatus.POSTED,
                persistLot(fixture), "2");
        persistIssueLine(workOrder, componentLine, fixture, MaterialIssueStatus.PENDING_APPROVAL, null, "4");
        WorkOrder other = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        persistIssueLine(other, persistComponentLine(other, fixture), fixture, MaterialIssueStatus.POSTED, null, "9");
        entityManager.clear();

        List<MaterialIssueLine> lines = issueLineRepository.findPostedByWorkOrder(workOrder.getWorkOrderId());

        assertThat(lines).extracting(MaterialIssueLine::getIssueLineId)
                .containsExactlyInAnyOrder(posted.getIssueLineId(), postedLot.getIssueLineId());
    }

    @Test
    void searchWip_keepsTheAskedStatusesOfThePlant_evenWithoutASearch() {
        Fixture fixture = persistFixture();
        WorkOrder running = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        WorkOrder done = persistWorkOrder(fixture, WorkOrderStatus.COMPLETED);
        persistWorkOrder(fixture, WorkOrderStatus.DRAFT);
        persistWorkOrder(persistFixture(), WorkOrderStatus.IN_PROGRESS);
        UUID plantId = fixture.plant().getPlantId();

        Page<WorkOrder> page = workOrderRepository.searchWip(plantId,
                EnumSet.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED), null, null, null, null, PageRequest.of(0, 20));
        Page<WorkOrder> byNumber = workOrderRepository.searchWip(plantId, EnumSet.of(WorkOrderStatus.COMPLETED), null,
                done.getWorkOrderNo().toLowerCase(), null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(WorkOrder::getWorkOrderId)
                .containsExactlyInAnyOrder(running.getWorkOrderId(), done.getWorkOrderId());
        assertThat(byNumber.getContent()).extracting(WorkOrder::getWorkOrderId).containsExactly(done.getWorkOrderId());
    }

    /** Pending rework of a work order = rework recorded − what retries processed; one row per work order with executions. */
    @Test
    void sumReworkByWorkOrderIds_splitsRetriesFromFreshWork() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);
        WorkOrderOperation operation = persistOperation(workOrder, 10);
        ProductionExecution source = persistExecution(workOrder, operation, null, "8", "1", "3");
        persistExecution(workOrder, operation, source.getProductionExecutionId(), "1", "0", "1");
        WorkOrder idle = persistWorkOrder(fixture, WorkOrderStatus.IN_PROGRESS);

        List<WorkOrderReworkTotals> rows = executionRepository.sumReworkByWorkOrderIds(
                List.of(workOrder.getWorkOrderId(), idle.getWorkOrderId()));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getWorkOrderId()).isEqualTo(workOrder.getWorkOrderId());
        assertThat(rows.get(0).getRework()).isEqualByComparingTo("4");
        assertThat(rows.get(0).getRetryProcessed()).isEqualByComparingTo("2");
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────────────

    private record Fixture(Plant plant, Warehouse warehouse, Item product, Item component, BomHeader bom, BomLine bomLine) {}


    private <T extends com.erp.manufacturing.common.audit.BaseEntity> T stamp(T entity) {
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private Fixture persistFixture() {
        String suffix = UUID.randomUUID().toString();
        Company company = entityManager.persistFlushFind(stamp(Company.builder().code("CO_" + suffix)
                .name("Company " + suffix).status(OrganizationStatus.ACTIVE).build()));
        Plant plant = entityManager.persistFlushFind(stamp(Plant.builder().company(company).code("PL_" + suffix)
                .name("Plant " + suffix).status(OrganizationStatus.ACTIVE).build()));
        Warehouse warehouse = entityManager.persistFlushFind(stamp(Warehouse.builder().plant(plant).code("WH_" + suffix)
                .name("Warehouse " + suffix).type(WarehouseType.RAW_MATERIAL).status(OrganizationStatus.ACTIVE).build()));
        Item product = entityManager.persistFlushFind(stamp(Item.builder().company(company).code("FG_" + suffix)
                .name("Product").type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).build()));
        Item component = entityManager.persistFlushFind(stamp(Item.builder().company(company).code("RM_" + suffix)
                .name("Steel").type(ItemType.RAW_MATERIAL).unit("KG").status(ItemStatus.ACTIVE).build()));
        BomHeader bom = entityManager.persistFlushFind(stamp(BomHeader.builder().company(company).parentItem(product)
                .revision("R1").bomCode("BOM-" + suffix).versionNumber(1).status(BomStatus.ACTIVE).build()));
        BomLine bomLine = entityManager.persistFlushFind(stamp(BomLine.builder().bom(bom).componentItem(component)
                .lineNo(1).quantityPer(BigDecimal.ONE).build()));
        return new Fixture(plant, warehouse, product, component, bom, bomLine);
    }

    private WorkOrder persistWorkOrder(Fixture fixture) {
        return entityManager.persistFlushFind(stamp(WorkOrder.builder()
                .company(fixture.plant.getCompany())
                .plant(fixture.plant)
                .workOrderNo("WO-" + UUID.randomUUID())
                .productItem(fixture.product)
                .bom(fixture.bom)
                .bomRevision("R1")
                .outputWarehouse(fixture.warehouse)
                .plannedQuantity(new BigDecimal("10"))
                .completedQuantity(BigDecimal.ZERO)
                .actualGoodQuantity(BigDecimal.ZERO)
                .actualScrapQuantity(BigDecimal.ZERO)
                .actualReworkQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.IN_PROGRESS)
                .build()));
    }

    private WorkOrderOperation persistOperation(WorkOrder workOrder, int sequence) {
        return entityManager.persistFlushFind(stamp(WorkOrderOperation.builder()
                .workOrder(workOrder).sequence(sequence).name("OP-" + sequence).workCenterCode("WC")
                .build()));
    }

    private ProductionExecution persistExecution(WorkOrder workOrder, WorkOrderOperation operation, UUID source,
                                                 String good, String scrap, String rework) {
        return entityManager.persistFlushFind(stamp(ProductionExecution.builder()
                .workOrder(workOrder)
                .operation(operation)
                .sourceExecutionId(source)
                .goodQuantity(new BigDecimal(good))
                .scrapQuantity(new BigDecimal(scrap))
                .reworkQuantity(new BigDecimal(rework))
                .idempotencyKey(UUID.randomUUID().toString())
                .build()));
    }


    private WorkOrderComponentLine persistComponentLine(WorkOrder workOrder, Fixture fixture) {
        return entityManager.persistFlushFind(stamp(WorkOrderComponentLine.builder()
                .workOrder(workOrder).bomLine(fixture.bomLine).componentItem(fixture.component).lineNo(1)
                .quantityPer(BigDecimal.ONE).requiredQuantity(BigDecimal.TEN).build()));
    }



    private WorkOrder persistWorkOrder(Fixture fixture, WorkOrderStatus status) {
        WorkOrder workOrder = persistWorkOrder(fixture);
        workOrder.setStatus(status);
        return entityManager.persistFlushFind(workOrder);
    }

    private InventoryLot persistLot(Fixture fixture) {
        return entityManager.persistFlushFind(stamp(InventoryLot.builder().item(fixture.component())
                .lotCode("LOT-" + UUID.randomUUID()).status(LotStatus.AVAILABLE).receivedAt(Instant.now()).build()));
    }

    private MaterialIssueLine persistIssueLine(WorkOrder workOrder, WorkOrderComponentLine componentLine, Fixture fixture,
                                               MaterialIssueStatus status, InventoryLot lot, String quantity) {
        MaterialIssue issue = entityManager.persistFlushFind(stamp(MaterialIssue.builder()
                .workOrder(workOrder).status(status)
                .idempotencyKey(UUID.randomUUID().toString()).postedAt(Instant.now()).build()));
        return entityManager.persistFlushFind(stamp(MaterialIssueLine.builder()
                .issue(issue).componentLine(componentLine).item(fixture.component()).warehouse(fixture.warehouse()).lot(lot)
                .quantity(new BigDecimal(quantity)).build()));
    }

    private MaterialReturn persistReturn(WorkOrder workOrder, Instant postedAt) {
        return entityManager.persistFlushFind(stamp(MaterialReturn.builder().workOrder(workOrder).reason("Unused")
                .postedAt(postedAt).idempotencyKey(UUID.randomUUID().toString()).build()));
    }

    private void persistReturnLine(MaterialReturn materialReturn, int lineNo, MaterialIssueLine source, Fixture fixture,
                                   String quantity) {
        StockMovement movement = entityManager.persistFlushFind(StockMovement.builder().item(fixture.component())
                .warehouse(fixture.warehouse()).lot(source.getLot()).movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN).quantity(new BigDecimal(quantity))
                .idempotencyKey(UUID.randomUUID().toString()).createdAt(Instant.now()).build());
        entityManager.persistAndFlush(stamp(MaterialReturnLine.builder().materialReturn(materialReturn).lineNo(lineNo)
                .sourceIssueLine(source).quantity(new BigDecimal(quantity)).stockMovement(movement).build()));
    }
}
