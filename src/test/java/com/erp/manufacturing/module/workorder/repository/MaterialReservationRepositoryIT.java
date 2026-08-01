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
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code MaterialReservationRepository.sumActiveRemainingByWorkOrderIds} (F9) against a real Postgres
 * Testcontainer.
 *
 * <p>Rule R7: the whole behaviour of this query is JPQL — the {@code status = ACTIVE} predicate, the
 * {@code sum(quantity - consumedQuantity)} arithmetic, and the {@code group by}. A mocked repository
 * returns whatever the test hands it and would prove none of them.
 *
 * <p>Why it matters that {@code ACTIVE} is enforced: this number feeds both
 * {@code WorkOrderComponentLineResponse.reservedQuantity} and the readiness screen. Counting released
 * or consumed reservations would tell a planner material is being held that is not.
 *
 * <p>Following the convention of {@link WorkOrderRepositoryIT}, every exclusion case also seeds a row
 * that must come back, so an empty result caused by a broken fixture cannot pass as a correct
 * exclusion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MaterialReservationRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    MaterialReservationRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void sumActiveRemainingByWorkOrderIds_sumsRemainingAcrossReservationsOfTheSameComponentLine() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);

        // 5 - 2 = 3 remaining, plus 4 - 0 = 4 ⇒ 7. Asserting the sum rather than the raw quantities
        // is what pins the "minus consumed" half of the formula.
        persistReservation(workOrder, line, fixture, MaterialReservationStatus.ACTIVE,
                new BigDecimal("5.000000"), new BigDecimal("2.000000"));
        persistReservation(workOrder, line, fixture, MaterialReservationStatus.ACTIVE,
                new BigDecimal("4.000000"), BigDecimal.ZERO);

        assertThat(reserved(workOrder))
                .containsExactly(Map.entry(line.getComponentLineId(), new BigDecimal("7.000000")));
    }

    /**
     * 🔴 The predicate a mock cannot check. A {@code RELEASED} reservation has given its material
     * back and a {@code CONSUMED} one has already been issued — counting either would report stock as
     * held twice.
     */
    @Test
    void sumActiveRemainingByWorkOrderIds_countsActiveReservationsOnly() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);

        persistReservation(workOrder, line, fixture, MaterialReservationStatus.ACTIVE,
                new BigDecimal("3.000000"), BigDecimal.ZERO);
        persistReservation(workOrder, line, fixture, MaterialReservationStatus.RELEASED,
                new BigDecimal("9.000000"), BigDecimal.ZERO);
        persistReservation(workOrder, line, fixture, MaterialReservationStatus.CONSUMED,
                new BigDecimal("7.000000"), BigDecimal.ZERO);

        assertThat(reserved(workOrder))
                .containsExactly(Map.entry(line.getComponentLineId(), new BigDecimal("3.000000")));
    }

    /**
     * The batch form has to keep the rows apart across work orders. It groups by
     * {@code componentLineId} alone and carries no work order dimension — safe only because component
     * line ids are globally unique, which is exactly what this case demonstrates.
     */
    @Test
    void sumActiveRemainingByWorkOrderIds_keepsComponentLinesOfDifferentWorkOrdersApart() {
        Fixture fixture = persistFixture();
        WorkOrder first = persistWorkOrder(fixture);
        WorkOrder second = persistWorkOrder(fixture);
        WorkOrderComponentLine firstLine = first.getComponentLines().get(0);
        WorkOrderComponentLine secondLine = second.getComponentLines().get(0);

        persistReservation(first, firstLine, fixture, MaterialReservationStatus.ACTIVE,
                new BigDecimal("2.000000"), BigDecimal.ZERO);
        persistReservation(second, secondLine, fixture, MaterialReservationStatus.ACTIVE,
                new BigDecimal("8.000000"), BigDecimal.ZERO);

        assertThat(reserved(first, second)).containsOnly(
                Map.entry(firstLine.getComponentLineId(), new BigDecimal("2.000000")),
                Map.entry(secondLine.getComponentLineId(), new BigDecimal("8.000000")));
    }

    /**
     * A {@code group by} returns <em>no row</em> for a component line with nothing reserved — it does
     * not return zero. Callers therefore have to default, and this pins that they must; the mapper
     * uses {@code getOrDefault(..., ZERO)} for exactly this reason.
     */
    @Test
    void sumActiveRemainingByWorkOrderIds_omitsComponentLinesWithNoActiveReservation() {
        Fixture fixture = persistFixture();
        WorkOrder withReservation = persistWorkOrder(fixture);
        WorkOrder withNone = persistWorkOrder(fixture);

        persistReservation(withReservation, withReservation.getComponentLines().get(0), fixture,
                MaterialReservationStatus.ACTIVE, new BigDecimal("2.000000"), BigDecimal.ZERO);

        assertThat(reserved(withReservation, withNone))
                .containsOnlyKeys(withReservation.getComponentLines().get(0).getComponentLineId());
    }

    private Map<UUID, BigDecimal> reserved(WorkOrder... workOrders) {
        List<UUID> ids = java.util.Arrays.stream(workOrders).map(WorkOrder::getWorkOrderId).toList();
        return repository.sumActiveRemainingByWorkOrderIds(ids).stream()
                .collect(Collectors.toMap(
                        ComponentQuantityProjection::getComponentLineId,
                        ComponentQuantityProjection::getQuantity));
    }

    // @DataJpaTest does not load JpaAuditingConfig/SecurityAuditorAware, so createdAt/updatedAt
    // (NOT NULL on BaseEntity subclasses) are set explicitly before persisting.

    private record Fixture(Plant plant, Warehouse warehouse, Item product, Item component,
                           BomHeader bom, BomLine bomLine) {}

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

        Item component = Item.builder().company(company).code("RAW_" + suffix)
                .name("Component " + suffix).type(ItemType.RAW_MATERIAL).unit("KG")
                .status(ItemStatus.ACTIVE).build();
        component.setCreatedAt(now);
        component.setUpdatedAt(now);
        component = entityManager.persistFlushFind(component);

        BomHeader bom = BomHeader.builder().company(company).parentItem(product).revision("R1")
                .status(BomStatus.ACTIVE).build();
        bom.setCreatedAt(now);
        bom.setUpdatedAt(now);
        bom = entityManager.persistFlushFind(bom);

        BomLine bomLine = BomLine.builder().bom(bom).componentItem(component).lineNo(1)
                .quantityPer(new BigDecimal("1.000000")).scrapRate(BigDecimal.ZERO).build();
        bomLine.setCreatedAt(now);
        bomLine.setUpdatedAt(now);
        bomLine = entityManager.persistFlushFind(bomLine);

        return new Fixture(plant, warehouse, product, component, bom, bomLine);
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
                .plannedQuantity(new BigDecimal("10.000000"))
                .completedQuantity(BigDecimal.ZERO)
                .actualGoodQuantity(BigDecimal.ZERO)
                .actualScrapQuantity(BigDecimal.ZERO)
                .actualReworkQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.RELEASED)
                .build();
        workOrder.setCreatedAt(now);
        workOrder.setUpdatedAt(now);

        WorkOrderComponentLine line = WorkOrderComponentLine.builder()
                .workOrder(workOrder)
                .bomLine(fixture.bomLine)
                .lineNo(1)
                .componentItem(fixture.component)
                .quantityPer(new BigDecimal("1.000000"))
                .scrapRate(BigDecimal.ZERO)
                .requiredQuantity(new BigDecimal("10.000000"))
                .issuedQuantity(BigDecimal.ZERO)
                .build();
        line.setCreatedAt(now);
        line.setUpdatedAt(now);
        workOrder.getComponentLines().add(line);

        return entityManager.persistFlushFind(workOrder);
    }

    private void persistReservation(WorkOrder workOrder,
                                    WorkOrderComponentLine line,
                                    Fixture fixture,
                                    MaterialReservationStatus status,
                                    BigDecimal quantity,
                                    BigDecimal consumedQuantity) {
        Instant now = Instant.now();
        MaterialReservation reservation = MaterialReservation.builder()
                .workOrder(workOrder)
                .componentLine(line)
                .item(fixture.component)
                .warehouse(fixture.warehouse)
                .quantity(quantity)
                .consumedQuantity(consumedQuantity)
                .status(status)
                .build();
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        entityManager.persistAndFlush(reservation);
    }
}
