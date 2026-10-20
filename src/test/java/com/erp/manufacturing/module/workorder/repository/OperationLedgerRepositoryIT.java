package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
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
import com.erp.manufacturing.module.workorder.domain.MaterialIssueLine;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.ProductionExecutionConsumption;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperationInput;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G8c (D1, D4, D7): the operation pools are derived in JPQL — fresh work and retries apart, inputs and transfers per
 * operation, consumption per issue line. A mocked repository cannot tell a wrong {@code case} branch or a missing
 * work order filter from a right one (rule R7), so these run against Postgres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OperationLedgerRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired ProductionExecutionRepository executionRepository;
    @Autowired WorkOrderOperationInputRepository inputRepository;
    @Autowired ProductionExecutionConsumptionRepository consumptionRepository;
    @Autowired MaterialIssueLineRepository issueLineRepository;
    @Autowired TestEntityManager entityManager;

    /** B218: retries never count against fresh input; rework stays the historical total of every execution. */
    @Test
    void sumByOperation_splitsFreshWorkFromRetriesPerOperation() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        WorkOrderOperation first = persistOperation(workOrder, 10);
        WorkOrderOperation second = persistOperation(workOrder, 20);
        ProductionExecution fresh = persistExecution(workOrder, first, null, "8", "1", "1");
        persistExecution(workOrder, first, fresh.getProductionExecutionId(), "0", "0", "1");
        persistExecution(workOrder, second, null, "5", "0", "0");
        persistExecution(persistWorkOrder(fixture), null, null, "3", "0", "0");

        Map<UUID, OperationExecutionTotals> totals = executionRepository.sumByOperation(workOrder.getWorkOrderId())
                .stream().collect(Collectors.toMap(OperationExecutionTotals::getOperationId, Function.identity()));

        assertThat(totals).containsOnlyKeys(first.getWorkOrderOperationId(), second.getWorkOrderOperationId());
        OperationExecutionTotals o1 = totals.get(first.getWorkOrderOperationId());
        assertThat(o1.getGood()).isEqualByComparingTo("8");
        assertThat(o1.getRework()).isEqualByComparingTo("2");
        assertThat(o1.getFreshProcessed()).isEqualByComparingTo("10");
        assertThat(o1.getRetryProcessed()).isEqualByComparingTo("1");
        assertThat(o1.getExecutionCount()).isEqualTo(2L);
        assertThat(totals.get(second.getWorkOrderOperationId()).getRetryProcessed()).isEqualByComparingTo("0");
    }

    @Test
    void sumRetryProcessedBySource_addsUpEveryRetryOfEachSource() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        WorkOrderOperation first = persistOperation(workOrder, 10);
        ProductionExecution source = persistExecution(workOrder, first, null, "5", "0", "3");
        ProductionExecution untouched = persistExecution(workOrder, first, null, "1", "0", "1");
        persistExecution(workOrder, first, source.getProductionExecutionId(), "1", "0", "0");
        persistExecution(workOrder, first, source.getProductionExecutionId(), "0", "1", "0");

        Map<UUID, BigDecimal> used = byRef(executionRepository.sumRetryProcessedBySource(
                List.of(source.getProductionExecutionId(), untouched.getProductionExecutionId())));

        assertThat(used).containsOnlyKeys(source.getProductionExecutionId());
        assertThat(used.get(source.getProductionExecutionId())).isEqualByComparingTo("2");
    }

    @Test
    void inputs_areSummedPerOperation_andTransfersPerSourceOperation_forOneWorkOrderOnly() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        WorkOrderOperation first = persistOperation(workOrder, 10);
        WorkOrderOperation second = persistOperation(workOrder, 20);
        persistInput(workOrder, first, null, "6");
        persistInput(workOrder, first, null, "4");
        persistInput(workOrder, second, first.getWorkOrderOperationId(), "5");
        WorkOrder other = persistWorkOrder(fixture);
        persistInput(other, persistOperation(other, 10), null, "99");

        Map<UUID, BigDecimal> inputs = byRef(inputRepository.sumInputByOperation(workOrder.getWorkOrderId()));
        Map<UUID, BigDecimal> transferred = byRef(inputRepository.sumTransferredBySourceOperation(workOrder.getWorkOrderId()));

        assertThat(inputs).containsOnlyKeys(first.getWorkOrderOperationId(), second.getWorkOrderOperationId());
        assertThat(inputs.get(first.getWorkOrderOperationId())).isEqualByComparingTo("10");
        assertThat(inputs.get(second.getWorkOrderOperationId())).isEqualByComparingTo("5");
        assertThat(transferred).containsOnlyKeys(first.getWorkOrderOperationId());
        assertThat(transferred.get(first.getWorkOrderOperationId())).isEqualByComparingTo("5");
    }

    /** B220: what was consumed per issue line, and the issue each line belongs to, in one query each. */
    @Test
    void consumption_isSummedPerIssueLine_andLinesComeBackWithTheirIssue() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        WorkOrderOperation first = persistOperation(workOrder, 10);
        WorkOrderComponentLine componentLine = persistComponentLine(workOrder, fixture);
        MaterialIssueLine line = persistIssueLine(workOrder, componentLine, fixture, "8");
        MaterialIssueLine other = persistIssueLine(workOrder, componentLine, fixture, "5");
        ProductionExecution a = persistExecution(workOrder, first, null, "2", "0", "0");
        ProductionExecution b = persistExecution(workOrder, first, null, "3", "0", "0");
        persistConsumption(a, line, "2");
        persistConsumption(b, line, "3.5");
        entityManager.clear();

        Map<UUID, BigDecimal> consumed = byRef(consumptionRepository.sumByIssueLineIds(
                List.of(line.getIssueLineId(), other.getIssueLineId())));
        List<MaterialIssueLine> lines = issueLineRepository.findWithIssueByIssueLineIdIn(List.of(line.getIssueLineId()));
        List<ProductionExecutionConsumption> rows = consumptionRepository
                .findByProductionExecutionProductionExecutionIdIn(List.of(b.getProductionExecutionId()));

        assertThat(consumed).containsOnlyKeys(line.getIssueLineId());
        assertThat(consumed.get(line.getIssueLineId())).isEqualByComparingTo("5.5");
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getIssue().getStatus()).isEqualTo(MaterialIssueStatus.POSTED);
        assertThat(lines.get(0).getIssue().getWorkOrder().getWorkOrderId()).isEqualTo(workOrder.getWorkOrderId());
        assertThat(rows).extracting(ProductionExecutionConsumption::getQuantity)
                .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("3.5"));
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────────────

    private record Fixture(Plant plant, Warehouse warehouse, Item product, Item component, BomHeader bom, BomLine bomLine) {}

    private static Map<UUID, BigDecimal> byRef(List<KeyedQuantityProjection> rows) {
        return rows.stream().collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
    }

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

    private void persistInput(WorkOrder workOrder, WorkOrderOperation operation, UUID sourceOperationId, String quantity) {
        entityManager.persistAndFlush(stamp(WorkOrderOperationInput.builder()
                .workOrder(workOrder).operation(operation).sourceOperationId(sourceOperationId)
                .quantity(new BigDecimal(quantity)).build()));
    }

    private WorkOrderComponentLine persistComponentLine(WorkOrder workOrder, Fixture fixture) {
        return entityManager.persistFlushFind(stamp(WorkOrderComponentLine.builder()
                .workOrder(workOrder).bomLine(fixture.bomLine).componentItem(fixture.component).lineNo(1)
                .quantityPer(BigDecimal.ONE).requiredQuantity(BigDecimal.TEN).build()));
    }

    private MaterialIssueLine persistIssueLine(WorkOrder workOrder, WorkOrderComponentLine componentLine, Fixture fixture,
                                               String quantity) {
        MaterialIssue issue = entityManager.persistFlushFind(stamp(MaterialIssue.builder()
                .workOrder(workOrder).status(MaterialIssueStatus.POSTED)
                .idempotencyKey(UUID.randomUUID().toString()).postedAt(Instant.now()).build()));
        return entityManager.persistFlushFind(stamp(MaterialIssueLine.builder()
                .issue(issue).componentLine(componentLine).item(fixture.component).warehouse(fixture.warehouse)
                .quantity(new BigDecimal(quantity)).build()));
    }

    private void persistConsumption(ProductionExecution execution, MaterialIssueLine line, String quantity) {
        entityManager.persistAndFlush(stamp(ProductionExecutionConsumption.builder()
                .productionExecution(execution).issueLineId(line.getIssueLineId())
                .quantity(new BigDecimal(quantity)).build()));
    }
}
