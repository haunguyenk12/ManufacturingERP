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
import org.springframework.data.domain.PageRequest;

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

    // ── CF-2 (B265): reservations on a lot that is no longer issuable do not cover the requirement ──────────

    /**
     * Only ACTIVE remaining on a lot that is not AVAILABLE or whose {@code expiresAt <= now} counts. Every exclusion
     * case is seeded next to rows that must count, and the AVAILABLE-future, undated and non-lot rows prove the
     * {@code join r.lot} plus the expiry predicate do not sweep everything in.
     */
    @Test
    void sumIneligibleActiveRemainingByWorkOrderId_countsExpiredAndHeldLotsOnly_activeRemainingOnly() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        WorkOrder otherWorkOrder = persistWorkOrder(fixture);
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        InventoryLot expired = persistLot(fixture, LotStatus.AVAILABLE, now.minus(1, java.time.temporal.ChronoUnit.DAYS));
        InventoryLot atNow = persistLot(fixture, LotStatus.AVAILABLE, now);
        InventoryLot hold = persistLot(fixture, LotStatus.HOLD, now.plus(1, java.time.temporal.ChronoUnit.DAYS));
        InventoryLot future = persistLot(fixture, LotStatus.AVAILABLE, now.plusSeconds(1));
        InventoryLot undated = persistLot(fixture, LotStatus.AVAILABLE, null);

        // counted: 4 - 1 = 3 (expired) + 1.5 (expires exactly now) + 2 (HOLD, not expired) = 6.5
        persistLotReservation(workOrder, line, fixture, expired, MaterialReservationStatus.ACTIVE,
                new BigDecimal("4.000000"), new BigDecimal("1.000000"));
        persistLotReservation(workOrder, line, fixture, atNow, MaterialReservationStatus.ACTIVE,
                new BigDecimal("1.500000"), BigDecimal.ZERO);
        persistLotReservation(workOrder, line, fixture, hold, MaterialReservationStatus.ACTIVE,
                new BigDecimal("2.000000"), BigDecimal.ZERO);
        // not counted: issuable lots, non-lot stock, released/consumed rows, another work order
        persistLotReservation(workOrder, line, fixture, future, MaterialReservationStatus.ACTIVE,
                new BigDecimal("5.000000"), BigDecimal.ZERO);
        persistLotReservation(workOrder, line, fixture, undated, MaterialReservationStatus.ACTIVE,
                new BigDecimal("1.000000"), BigDecimal.ZERO);
        persistReservation(workOrder, line, fixture, MaterialReservationStatus.ACTIVE,
                new BigDecimal("6.000000"), BigDecimal.ZERO);
        persistLotReservation(workOrder, line, fixture, expired, MaterialReservationStatus.RELEASED,
                new BigDecimal("9.000000"), BigDecimal.ZERO);
        persistLotReservation(workOrder, line, fixture, expired, MaterialReservationStatus.CONSUMED,
                new BigDecimal("7.000000"), new BigDecimal("7.000000"));
        persistLotReservation(otherWorkOrder, otherWorkOrder.getComponentLines().get(0), fixture, expired,
                MaterialReservationStatus.ACTIVE, new BigDecimal("8.000000"), BigDecimal.ZERO);
        entityManager.clear();

        List<ComponentQuantityProjection> ineligible =
                repository.sumIneligibleActiveRemainingByWorkOrderId(workOrder.getWorkOrderId(), now);

        assertThat(ineligible).singleElement().satisfies(row -> {
            assertThat(row.getComponentLineId()).isEqualTo(line.getComponentLineId());
            assertThat(row.getQuantity()).isEqualByComparingTo("6.5");
        });
        // reservedQuantity keeps its meaning (F9): every ACTIVE remaining, eligible or not = 3 + 1.5 + 2 + 5 + 1 + 6.
        assertThat(repository.sumActiveRemainingByWorkOrderId(workOrder.getWorkOrderId()))
                .singleElement().satisfies(row -> assertThat(row.getQuantity()).isEqualByComparingTo("18.5"));
    }

    /** A work order whose reservations all sit on issuable lots has no ineligible row at all (group by, not zero). */
    @Test
    void sumIneligibleActiveRemainingByWorkOrderId_allLotsIssuable_returnsNoRow() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        persistLotReservation(workOrder, line, fixture,
                persistLot(fixture, LotStatus.AVAILABLE, now.plus(2, java.time.temporal.ChronoUnit.DAYS)),
                MaterialReservationStatus.ACTIVE, new BigDecimal("3.000000"), BigDecimal.ZERO);
        entityManager.clear();

        assertThat(repository.sumIneligibleActiveRemainingByWorkOrderId(workOrder.getWorkOrderId(), now)).isEmpty();
    }

    private InventoryLot persistLot(Fixture fixture, LotStatus status, Instant expiresAt) {
        Instant now = Instant.now();
        InventoryLot lot = InventoryLot.builder().item(fixture.component).lotCode("LOT_" + UUID.randomUUID())
                .status(status).receivedAt(now).expiresAt(expiresAt).build();
        lot.setCreatedAt(now);
        lot.setUpdatedAt(now);
        return entityManager.persistFlushFind(lot);
    }

    private void persistLotReservation(WorkOrder workOrder, WorkOrderComponentLine line, Fixture fixture,
                                       InventoryLot lot, MaterialReservationStatus status,
                                       BigDecimal quantity, BigDecimal consumedQuantity) {
        Instant now = Instant.now();
        MaterialReservation reservation = MaterialReservation.builder()
                .workOrder(workOrder).componentLine(line).item(fixture.component).warehouse(fixture.warehouse)
                .lot(lot).quantity(quantity).consumedQuantity(consumedQuantity).status(status).build();
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        entityManager.persistAndFlush(reservation);
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
                .bomCode("BOM-R1").versionNumber(1)
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

    /**
     * G4-4: the warehouse reservation list. The component here is not lot-tracked, so every row has
     * {@code lot_id IS NULL} — an inner join on {@code lot} would return nothing at all.
     */
    @Test
    void searchByWarehouse_keepsReservationsWithoutALot_andHonoursStatusAndLotFilters() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        persistReservation(workOrder, line, fixture, MaterialReservationStatus.ACTIVE,
                new BigDecimal("5.000000"), new BigDecimal("2.000000"));
        persistReservation(workOrder, line, fixture, MaterialReservationStatus.RELEASED,
                new BigDecimal("1.000000"), BigDecimal.ZERO);
        entityManager.clear();
        UUID warehouseId = fixture.warehouse.getWarehouseId();

        var all = repository.searchByWarehouse(warehouseId, null, null, null, PageRequest.of(0, 20));
        var active = repository.searchByWarehouse(warehouseId, fixture.component.getItemId(), null,
                MaterialReservationStatus.ACTIVE, PageRequest.of(0, 20));
        var otherLot = repository.searchByWarehouse(warehouseId, null, UUID.randomUUID(), null, PageRequest.of(0, 20));

        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(active.getContent()).singleElement().satisfies(r -> {
            assertThat(r.remainingQuantity()).isEqualByComparingTo("3");
            assertThat(r.getWorkOrder().getWorkOrderNo()).isEqualTo(workOrder.getWorkOrderNo());
        });
        assertThat(otherLot.getTotalElements()).isZero();
    }

    // ── G6a: sales-order reservations share the table ──────────────────────

    /**
     * 🔴 A sales-order reservation has no work order and no component line. The warehouse list used to
     * inner-join both, which would hide every stock hold made for a customer from the very screen that
     * answers "why is this stock not free".
     */
    @Test
    void searchByWarehouse_returnsSalesOrderReservationsNextToWorkOrderOnes() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        persistReservation(workOrder, workOrder.getComponentLines().get(0), fixture,
                MaterialReservationStatus.ACTIVE, new BigDecimal("2.000000"), BigDecimal.ZERO);
        SalesLine sales = persistSalesOrderLine(fixture);
        persistSalesReservation(sales, fixture, MaterialReservationStatus.ACTIVE, new BigDecimal("3.000000"));
        entityManager.clear();

        var page = repository.searchByWarehouse(fixture.warehouse.getWarehouseId(), null, null, null,
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).filteredOn(MaterialReservation::isForSalesOrder).singleElement()
                .satisfies(r -> {
                    assertThat(r.getWorkOrder()).isNull();
                    assertThat(r.getSalesOrderLineId()).isEqualTo(sales.lineId());
                });
    }

    @Test
    void sumActiveRemainingBySalesOrderLineIds_countsActiveHoldsOnly() {
        Fixture fixture = persistFixture();
        SalesLine sales = persistSalesOrderLine(fixture);
        persistSalesReservation(sales, fixture, MaterialReservationStatus.ACTIVE, new BigDecimal("3.000000"));
        persistSalesReservation(sales, fixture, MaterialReservationStatus.ACTIVE, new BigDecimal("1.500000"));
        persistSalesReservation(sales, fixture, MaterialReservationStatus.RELEASED, new BigDecimal("9.000000"));
        persistSalesReservation(sales, fixture, MaterialReservationStatus.CANCELLED, new BigDecimal("7.000000"));

        var rows = repository.sumActiveRemainingBySalesOrderLineIds(List.of(sales.lineId()));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getSalesOrderLineId()).isEqualTo(sales.lineId());
            assertThat(row.getQuantity()).isEqualByComparingTo("4.5");
        });
    }

    /** V74 {@code chk_material_reservations_single_owner}: a row cannot belong to both owners. */
    @Test
    void singleOwnerCheck_refusesARowWithBothOwners() {
        Fixture fixture = persistFixture();
        WorkOrder workOrder = persistWorkOrder(fixture);
        SalesLine sales = persistSalesOrderLine(fixture);
        MaterialReservation both = reservationRow(fixture)
                .workOrder(workOrder).componentLine(workOrder.getComponentLines().get(0))
                .salesOrderId(sales.orderId()).salesOrderLineId(sales.lineId()).build();
        stamp(both);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> entityManager.persistAndFlush(both))
                .rootCause().hasMessageContaining("chk_material_reservations_single_owner");
    }

    /** …and cannot belong to nobody (both nullable since V74). */
    @Test
    void singleOwnerCheck_refusesARowWithNoOwner() {
        Fixture fixture = persistFixture();
        MaterialReservation none = reservationRow(fixture).build();
        stamp(none);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> entityManager.persistAndFlush(none))
                .rootCause().hasMessageContaining("chk_material_reservations_single_owner");
    }

    private MaterialReservation.MaterialReservationBuilder reservationRow(Fixture fixture) {
        return MaterialReservation.builder()
                .item(fixture.component).warehouse(fixture.warehouse)
                .quantity(BigDecimal.ONE).consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE);
    }

    private static void stamp(MaterialReservation reservation) {
        Instant now = Instant.now();
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
    }

    private record SalesLine(UUID orderId, UUID lineId) {}

    private SalesLine persistSalesOrderLine(Fixture fixture) {
        Instant now = Instant.now();
        var order = com.erp.manufacturing.module.sales.domain.SalesOrder.builder()
                .company(fixture.plant.getCompany()).plant(fixture.plant)
                .orderNo("SO-" + UUID.randomUUID()).customerName("ACME")
                .orderDate(java.time.LocalDate.of(2026, 10, 1))
                .status(com.erp.manufacturing.module.sales.domain.SalesOrderStatus.CONFIRMED).build();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        var line = com.erp.manufacturing.module.sales.domain.SalesOrderLine.builder()
                .salesOrder(order).item(fixture.product).lineNo(1)
                .orderedQuantity(new BigDecimal("10.000000")).fulfilledQuantity(BigDecimal.ZERO)
                .dueDate(java.time.LocalDate.of(2026, 10, 20)).build();
        line.setCreatedAt(now);
        line.setUpdatedAt(now);
        order.getLines().add(line);
        order = entityManager.persistFlushFind(order);
        return new SalesLine(order.getSalesOrderId(), order.getLines().get(0).getSalesOrderLineId());
    }

    private void persistSalesReservation(SalesLine sales, Fixture fixture,
                                         MaterialReservationStatus status, BigDecimal quantity) {
        Instant now = Instant.now();
        MaterialReservation reservation = MaterialReservation.builder()
                .salesOrderId(sales.orderId())
                .salesOrderLineId(sales.lineId())
                .item(fixture.product)
                .warehouse(fixture.warehouse)
                .quantity(quantity)
                .consumedQuantity(BigDecimal.ZERO)
                .status(status)
                .build();
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        entityManager.persistAndFlush(reservation);
    }
}
