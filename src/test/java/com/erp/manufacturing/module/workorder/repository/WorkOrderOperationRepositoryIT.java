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
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the two Capacity Board (C2-8) JPQL queries against a real Postgres Testcontainer.
 *
 * <p>Both use {@code function('timezone', plant.timezone, plannedStartAt)} — the first use of this
 * escape hatch anywhere in the repo (no native query precedent existed before C2-8) — to bucket by
 * the plant's <em>local</em> calendar date, not the UTC date the {@code Instant} column stores. A
 * mocked repository cannot prove this conversion actually runs in the right direction; only Postgres
 * can (rule R7, same lesson as the {@code lower(bytea)} hazard in CLAUDE.md §0.24).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkOrderOperationRepositoryIT extends AbstractPostgresIntegrationTest {

    private static final List<WorkOrderStatus> LOAD_STATUSES =
            List.of(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED);

    @Autowired
    WorkOrderOperationRepository repository;

    @Autowired
    TestEntityManager entityManager;

    /**
     * 2026-01-06T03:00:00Z is 2026-01-05 22:00 in {@code America/New_York} (EST, UTC-5, no DST in
     * January) — deliberately chosen so the UTC calendar date (Jan 6) and the plant-local calendar
     * date (Jan 5) disagree. If the query bucketed by UTC date instead of plant timezone, this
     * operation would show up on Jan 6, not Jan 5, and both assertions below would fail together.
     */
    @Test
    void searchCapacityBoard_bucketsByThePlantsLocalDate_notUtc() {
        Fixture fixture = persistFixture("America/New_York");
        Instant plannedStart = Instant.parse("2026-01-06T03:00:00Z");
        WorkOrderOperation operation = persistOperation(fixture, WorkOrderStatus.RELEASED, plannedStart);

        assertThat(pageOf(fixture, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), null, null))
                .extracting(WorkOrderOperation::getWorkOrderOperationId)
                .containsExactly(operation.getWorkOrderOperationId());
        assertThat(pageOf(fixture, LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 6), null, null))
                .isEmpty();
    }

    @Test
    void searchCapacityBoard_excludesOperationsWithoutAWorkCenter() {
        Fixture fixture = persistFixture("UTC");
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.RELEASED);
        WorkOrderOperation noWorkCenter = WorkOrderOperation.builder()
                .workOrder(workOrder).sequence(1).name("Op").workCenterCode("WC-LEGACY")
                .plannedStartAt(Instant.parse("2026-01-05T08:00:00Z"))
                .plannedEndAt(Instant.parse("2026-01-05T09:00:00Z"))
                .build();
        noWorkCenter.setCreatedAt(Instant.now());
        noWorkCenter.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(noWorkCenter);

        assertThat(pageOf(fixture, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null)).isEmpty();
    }

    @Test
    void searchCapacityBoard_excludesOperationsNeverScheduled() {
        Fixture fixture = persistFixture("UTC");
        WorkOrder workOrder = persistWorkOrder(fixture, WorkOrderStatus.DRAFT);
        WorkOrderOperation neverReleased = WorkOrderOperation.builder()
                .workOrder(workOrder).sequence(1).name("Op").workCenterCode(fixture.workCenter.getCode())
                .workCenter(fixture.workCenter)
                .build();
        neverReleased.setCreatedAt(Instant.now());
        neverReleased.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(neverReleased);

        assertThat(pageOf(fixture, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null)).isEmpty();
    }

    @Test
    void searchCapacityBoard_filtersByWorkCenterIdAndStatus() {
        Fixture fixture = persistFixture("UTC");
        WorkCenter otherWorkCenter = persistWorkCenter(fixture.plant, "WC-2");
        Instant day = Instant.parse("2026-01-05T08:00:00Z");

        WorkOrderOperation wanted = persistOperation(fixture, WorkOrderStatus.RELEASED, day);
        persistOperation(new Fixture(fixture.plant, otherWorkCenter, fixture.warehouse, fixture.product, fixture.bom),
                WorkOrderStatus.RELEASED, day);
        persistOperation(fixture, WorkOrderStatus.CANCELLED, day);

        List<WorkOrderOperation> found = pageOf(fixture, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                fixture.workCenter.getWorkCenterId(), WorkOrderStatus.RELEASED);

        assertThat(found).extracting(WorkOrderOperation::getWorkOrderOperationId)
                .containsExactly(wanted.getWorkOrderOperationId());
    }

    @Test
    void aggregateExistingLoad_sumsMinutesGroupedByWorkCenterAndPlantLocalDay() {
        Fixture fixture = persistFixture("UTC");
        Instant day = Instant.parse("2026-01-05T08:00:00Z");
        // setup=10, run=5/unit, plannedQuantity=10 (persistWorkOrder default) => 10 + 5*10 = 60 min each
        persistOperation(fixture, WorkOrderStatus.RELEASED, day);
        persistOperation(fixture, WorkOrderStatus.IN_PROGRESS, day);

        List<CapacityLoadProjection> load = repository.aggregateExistingLoad(
                fixture.plant.getPlantId(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), LOAD_STATUSES);

        assertThat(load).hasSize(1);
        assertThat(load.get(0).getWorkCenterId()).isEqualTo(fixture.workCenter.getWorkCenterId());
        assertThat(load.get(0).getDay()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(load.get(0).getLoadMinutes()).isEqualByComparingTo("120.000000");
    }

    /** {@code CANCELLED} schedules no longer represent real load — CapacityBoardService's contract. */
    @Test
    void aggregateExistingLoad_excludesCancelledWorkOrders() {
        Fixture fixture = persistFixture("UTC");
        persistOperation(fixture, WorkOrderStatus.CANCELLED, Instant.parse("2026-01-05T08:00:00Z"));

        List<CapacityLoadProjection> load = repository.aggregateExistingLoad(
                fixture.plant.getPlantId(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), LOAD_STATUSES);

        assertThat(load).isEmpty();
    }

    private List<WorkOrderOperation> pageOf(Fixture fixture, LocalDate from, LocalDate to,
                                             UUID workCenterId, WorkOrderStatus status) {
        return repository.searchCapacityBoard(
                fixture.plant.getPlantId(), workCenterId, status, from, to, PageRequest.of(0, 20)).getContent();
    }

    private record Fixture(Plant plant, WorkCenter workCenter, Warehouse warehouse, Item product, BomHeader bom) {}

    private Fixture persistFixture(String timezone) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Company company = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        company = entityManager.persistFlushFind(company);

        Plant plant = Plant.builder().company(company).code("PL_" + suffix).name("Plant " + suffix)
                .timezone(timezone).status(OrganizationStatus.ACTIVE).build();
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

        WorkCenter workCenter = persistWorkCenter(plant, "WC_" + suffix);

        return new Fixture(plant, workCenter, warehouse, product, bom);
    }

    private WorkCenter persistWorkCenter(Plant plant, String code) {
        Instant now = Instant.now();
        WorkCenter workCenter = WorkCenter.builder().plant(plant).code(code).name("Work Center " + code)
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build();
        workCenter.setCreatedAt(now);
        workCenter.setUpdatedAt(now);
        return entityManager.persistFlushFind(workCenter);
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
                .plannedQuantity(BigDecimal.TEN)
                .completedQuantity(BigDecimal.ZERO)
                .status(status)
                .build();
        workOrder.setCreatedAt(now);
        workOrder.setUpdatedAt(now);
        return entityManager.persistFlushFind(workOrder);
    }

    private WorkOrderOperation persistOperation(Fixture fixture, WorkOrderStatus workOrderStatus, Instant plannedStartAt) {
        WorkOrder workOrder = persistWorkOrder(fixture, workOrderStatus);
        WorkOrderOperation operation = WorkOrderOperation.builder()
                .workOrder(workOrder)
                .sequence(1)
                .name("Assembly")
                .workCenterCode(fixture.workCenter.getCode())
                .workCenter(fixture.workCenter)
                .setupMinutes(new BigDecimal("10"))
                .runMinutesPerUnit(new BigDecimal("5"))
                .plannedStartAt(plannedStartAt)
                .plannedEndAt(plannedStartAt.plusSeconds(3600))
                .build();
        Instant now = Instant.now();
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        return entityManager.persistFlushFind(operation);
    }
}
