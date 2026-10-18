package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrder;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderLine;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import com.erp.manufacturing.module.purchasing.domain.Supplier;
import com.erp.manufacturing.module.purchasing.domain.SupplierStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the open purchase-order supply JPQL (D4.1, debt #16) against a real Postgres Testcontainer.
 *
 * <p>The netting-relevant filters — order status, remaining quantity {@code > 0}, warehouse scope and
 * plant scope — live in the query rather than in Java, so per rule {@code R7} this is the only place
 * they can actually be tested. Every exclusion case also seeds one line that <em>must</em> be
 * returned, so a result that is empty because the fixture is broken cannot pass as a correct
 * exclusion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PurchaseOrderRepositoryIT extends AbstractPostgresIntegrationTest {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 8, 1);
    private static final List<PurchaseOrderStatus> OPEN_STATUSES = List.of(
            PurchaseOrderStatus.CONFIRMED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED);

    @Autowired
    PurchaseOrderRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void aggregateOpenSupply_sumsRemainingQuantityAcrossOrdersOfTheSameItem() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Supplier supplier = persistSupplier(company);
        Item item = persistItem(company);

        PurchaseOrder sent = persistOrder(warehouse, supplier, "PO-SENT", PurchaseOrderStatus.CONFIRMED);
        persistLine(sent, item, new BigDecimal("100.000000"), BigDecimal.ZERO);
        PurchaseOrder partial = persistOrder(warehouse, supplier, "PO-PARTIAL",
                PurchaseOrderStatus.PARTIALLY_RECEIVED);
        persistLine(partial, item, new BigDecimal("50.000000"), new BigDecimal("20.000000"));

        List<PurchaseOrderSupplyProjection> supply = repository.aggregateOpenSupply(
                company.getCompanyId(),
                warehouse.getPlant().getPlantId(),
                List.of(warehouse.getWarehouseId()),
                List.of(item.getItemId()),
                OPEN_STATUSES);

        assertThat(supply).hasSize(1);
        assertThat(supply.get(0).getItemId()).isEqualTo(item.getItemId());
        assertThat(supply.get(0).getOpenSupplyQuantity()).isEqualByComparingTo("130");  // 100 + (50 − 20)
    }

    @Test
    void aggregateOpenSupply_excludesDraftReceivedAndCancelledOrders() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Supplier supplier = persistSupplier(company);
        Item item = persistItem(company);

        persistLine(persistOrder(warehouse, supplier, "PO-DRAFT", PurchaseOrderStatus.DRAFT),
                item, new BigDecimal("10.000000"), BigDecimal.ZERO);
        persistLine(persistOrder(warehouse, supplier, "PO-RECEIVED", PurchaseOrderStatus.FULLY_RECEIVED),
                item, new BigDecimal("10.000000"), BigDecimal.ZERO);
        persistLine(persistOrder(warehouse, supplier, "PO-CANCELLED", PurchaseOrderStatus.CANCELLED),
                item, new BigDecimal("10.000000"), BigDecimal.ZERO);
        persistLine(persistOrder(warehouse, supplier, "PO-SENT", PurchaseOrderStatus.CONFIRMED),
                item, new BigDecimal("7.000000"), BigDecimal.ZERO);

        List<PurchaseOrderSupplyProjection> supply = repository.aggregateOpenSupply(
                company.getCompanyId(),
                warehouse.getPlant().getPlantId(),
                List.of(warehouse.getWarehouseId()),
                List.of(item.getItemId()),
                OPEN_STATUSES);

        assertThat(supply).hasSize(1);
        assertThat(supply.get(0).getOpenSupplyQuantity()).isEqualByComparingTo("7");
    }

    /**
     * A PARTIALLY_RECEIVED order keeps its fully received lines in the table. Counting them would
     * inflate scheduled receipts by material that has already landed in stock and is therefore
     * already in {@code eligibleOnHand} — i.e. it would be double-counted.
     */
    @Test
    void aggregateOpenSupply_excludesFullyReceivedLinesOfAPartiallyReceivedOrder() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Supplier supplier = persistSupplier(company);
        Item closedItem = persistItem(company);
        Item openItem = persistItem(company);

        PurchaseOrder order = persistOrder(warehouse, supplier, "PO-001",
                PurchaseOrderStatus.PARTIALLY_RECEIVED);
        persistLine(order, closedItem, new BigDecimal("10.000000"), new BigDecimal("10.000000"));
        persistLine(order, openItem, new BigDecimal("10.000000"), new BigDecimal("9.999999"));

        List<PurchaseOrderSupplyProjection> supply = repository.aggregateOpenSupply(
                company.getCompanyId(),
                warehouse.getPlant().getPlantId(),
                List.of(warehouse.getWarehouseId()),
                List.of(closedItem.getItemId(), openItem.getItemId()),
                OPEN_STATUSES);

        assertThat(supply).hasSize(1);
        assertThat(supply.get(0).getItemId()).isEqualTo(openItem.getItemId());
        assertThat(supply.get(0).getOpenSupplyQuantity()).isEqualByComparingTo("0.000001");
    }

    @Test
    void aggregateOpenSupply_excludesOrdersDeliveringToAWarehouseOutsideTheRunScope() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Warehouse otherWarehouse = persistWarehouseIn(warehouse.getPlant());
        Supplier supplier = persistSupplier(company);
        Item item = persistItem(company);

        persistLine(persistOrder(otherWarehouse, supplier, "PO-OTHER-WH", PurchaseOrderStatus.CONFIRMED),
                item, new BigDecimal("40.000000"), BigDecimal.ZERO);
        persistLine(persistOrder(warehouse, supplier, "PO-IN-SCOPE", PurchaseOrderStatus.CONFIRMED),
                item, new BigDecimal("6.000000"), BigDecimal.ZERO);

        List<PurchaseOrderSupplyProjection> supply = repository.aggregateOpenSupply(
                company.getCompanyId(),
                warehouse.getPlant().getPlantId(),
                List.of(warehouse.getWarehouseId()),
                List.of(item.getItemId()),
                OPEN_STATUSES);

        assertThat(supply).hasSize(1);
        assertThat(supply.get(0).getOpenSupplyQuantity()).isEqualByComparingTo("6");
    }

    @Test
    void aggregateOpenSupply_excludesOrdersOfAnotherPlant() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Warehouse foreignWarehouse = persistWarehouseIn(persistPlantIn(company));
        Supplier supplier = persistSupplier(company);
        Item item = persistItem(company);

        persistLine(persistOrder(foreignWarehouse, supplier, "PO-OTHER-PLANT", PurchaseOrderStatus.CONFIRMED),
                item, new BigDecimal("40.000000"), BigDecimal.ZERO);
        persistLine(persistOrder(warehouse, supplier, "PO-IN-SCOPE", PurchaseOrderStatus.CONFIRMED),
                item, new BigDecimal("5.000000"), BigDecimal.ZERO);

        List<PurchaseOrderSupplyProjection> supply = repository.aggregateOpenSupply(
                company.getCompanyId(),
                warehouse.getPlant().getPlantId(),
                // Both warehouses are named on purpose: the plant filter, not the warehouse list,
                // must be what keeps the other plant's order out.
                List.of(warehouse.getWarehouseId(), foreignWarehouse.getWarehouseId()),
                List.of(item.getItemId()),
                OPEN_STATUSES);

        assertThat(supply).hasSize(1);
        assertThat(supply.get(0).getOpenSupplyQuantity()).isEqualByComparingTo("5");
    }

    // ── G7a ────────────────────────────────────────────────────────────────

    /** G7a: a closed remainder is no longer expected — open = ordered − received − closed. */
    @Test
    void aggregateOpenSupply_subtractsTheClosedQuantity() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Supplier supplier = persistSupplier(company);
        Item item = persistItem(company);
        Item fullyClosed = persistItem(company);

        PurchaseOrder order = persistOrder(warehouse, supplier, "PO-CLOSED-PART", PurchaseOrderStatus.PARTIALLY_RECEIVED);
        PurchaseOrderLine open = persistLine(order, item, new BigDecimal("10.000000"), new BigDecimal("2.000000"));
        open.setClosedQuantity(new BigDecimal("3.000000"));
        PurchaseOrderLine closed = persistLine(order, fullyClosed, new BigDecimal("10.000000"), new BigDecimal("4.000000"));
        closed.setClosedQuantity(new BigDecimal("6.000000"));
        entityManager.flush();

        List<PurchaseOrderSupplyProjection> supply = repository.aggregateOpenSupply(
                company.getCompanyId(),
                warehouse.getPlant().getPlantId(),
                List.of(warehouse.getWarehouseId()),
                List.of(item.getItemId(), fullyClosed.getItemId()),
                OPEN_STATUSES);

        assertThat(supply).singleElement().satisfies(row -> {
            assertThat(row.getItemId()).isEqualTo(item.getItemId());
            assertThat(row.getOpenSupplyQuantity()).isEqualByComparingTo("5");
        });
    }

    /** §0.24: an untyped null search term inside concat/like makes Postgres pick bytea and fail the query. */
    @Test
    void search_withoutTermOrDates_returnsEveryOrderOfThePlant() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Supplier supplier = persistSupplier(company);
        persistOrder(warehouse, supplier, "PO-A", PurchaseOrderStatus.DRAFT);
        persistOrder(warehouse, supplier, "PO-B", PurchaseOrderStatus.CLOSED);

        var page = repository.search(company.getCompanyId(), warehouse.getPlant().getPlantId(), null, null, null,
                null, null, null, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_matchesNumberReferenceSupplierCodeAndName_caseInsensitively() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Supplier acme = persistSupplier(company);
        acme.setName("ACME Metals");
        Supplier other = persistSupplier(company);
        PurchaseOrder byReference = persistOrder(warehouse, other, "PO-REF", PurchaseOrderStatus.DRAFT);
        byReference.setReference("Delivery-XYZ-7");
        PurchaseOrder bySupplier = persistOrder(warehouse, acme, "PO-SUP", PurchaseOrderStatus.DRAFT);
        PurchaseOrder byNumber = persistOrder(warehouse, other, "PO-NUMBERMATCH", PurchaseOrderStatus.DRAFT);
        persistOrder(warehouse, other, "PO-NOISE", PurchaseOrderStatus.DRAFT);
        entityManager.flush();
        UUID companyId = company.getCompanyId();
        UUID plantId = warehouse.getPlant().getPlantId();
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);

        assertThat(repository.search(companyId, plantId, null, null, null, "xyz-7", null, null, pageable).getContent())
                .containsExactly(byReference);
        assertThat(repository.search(companyId, plantId, null, null, null, "acme met", null, null, pageable).getContent())
                .containsExactly(bySupplier);
        assertThat(repository.search(companyId, plantId, null, null, null, acme.getCode().toLowerCase(), null, null, pageable)
                .getContent()).containsExactly(bySupplier);
        assertThat(repository.search(companyId, plantId, null, null, null, "numbermatch", null, null, pageable).getContent())
                .containsExactly(byNumber);
    }

    @Test
    void search_orderDateRangeIsInclusiveOnBothEnds() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Supplier supplier = persistSupplier(company);
        PurchaseOrder before = persistOrder(warehouse, supplier, "PO-BEFORE", PurchaseOrderStatus.DRAFT);
        before.setOrderDate(ORDER_DATE.minusDays(1));
        PurchaseOrder first = persistOrder(warehouse, supplier, "PO-FIRST", PurchaseOrderStatus.DRAFT);
        PurchaseOrder last = persistOrder(warehouse, supplier, "PO-LAST", PurchaseOrderStatus.DRAFT);
        last.setOrderDate(ORDER_DATE.plusDays(2));
        PurchaseOrder after = persistOrder(warehouse, supplier, "PO-AFTER", PurchaseOrderStatus.DRAFT);
        after.setOrderDate(ORDER_DATE.plusDays(3));
        entityManager.flush();

        var page = repository.search(company.getCompanyId(), warehouse.getPlant().getPlantId(), null, null, null,
                null, ORDER_DATE, ORDER_DATE.plusDays(2), org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).containsExactlyInAnyOrder(first, last);
    }

    @Test
    void nextOrderNumber_neverRepeats() {
        long first = repository.nextOrderNumber();
        long second = repository.nextOrderNumber();

        assertThat(second).isGreaterThan(first);
    }

    /** V76: the DB itself refuses received + closed above ordered — the service is not the only guard. */
    @Test
    void receivedPlusClosedAboveOrdered_isRejectedByTheDatabase() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        PurchaseOrderLine line = persistLine(persistOrder(warehouse, persistSupplier(company), "PO-CHK",
                PurchaseOrderStatus.CLOSED), persistItem(company), new BigDecimal("10"), new BigDecimal("6"));
        line.setClosedQuantity(new BigDecimal("4.000001"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> entityManager.flush())
                .hasMessageContaining("chk_po_lines_received_closed_within_ordered");
    }

    // @DataJpaTest does not load JpaAuditingConfig/SecurityAuditorAware, so createdAt/updatedAt
    // (NOT NULL on BaseEntity subclasses) are set explicitly before persisting.

    private Warehouse persistWarehouse() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Company company = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        return persistWarehouseIn(persistPlantIn(entityManager.persistFlushFind(company)));
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

    private Warehouse persistWarehouseIn(Plant plant) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Warehouse warehouse = Warehouse.builder().plant(plant).code("WH_" + suffix)
                .name("Warehouse " + suffix).type(WarehouseType.GENERAL)
                .status(OrganizationStatus.ACTIVE).build();
        warehouse.setCreatedAt(now);
        warehouse.setUpdatedAt(now);
        return entityManager.persistFlushFind(warehouse);
    }

    private Supplier persistSupplier(Company company) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Supplier supplier = Supplier.builder().company(company).code("SUP_" + suffix)
                .name("Supplier " + suffix).status(SupplierStatus.ACTIVE).build();
        supplier.setCreatedAt(now);
        supplier.setUpdatedAt(now);
        return entityManager.persistFlushFind(supplier);
    }

    private Item persistItem(Company company) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Item item = Item.builder().company(company).code("ITEM_" + suffix).name("Item " + suffix)
                .type(ItemType.RAW_MATERIAL).unit("EA").status(ItemStatus.ACTIVE).build();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return entityManager.persistFlushFind(item);
    }

    private PurchaseOrder persistOrder(Warehouse warehouse, Supplier supplier, String orderNo,
                                       PurchaseOrderStatus status) {
        Instant now = Instant.now();

        PurchaseOrder order = PurchaseOrder.builder()
                .company(warehouse.getPlant().getCompany())
                .plant(warehouse.getPlant())
                .warehouse(warehouse)
                .supplier(supplier)
                .purchaseOrderNo(orderNo + "_" + UUID.randomUUID())
                .status(status)
                .orderDate(ORDER_DATE)
                .expectedDate(ORDER_DATE.plusDays(14))
                .build();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return entityManager.persistFlushFind(order);
    }

    private PurchaseOrderLine persistLine(PurchaseOrder order, Item item,
                                          BigDecimal ordered, BigDecimal received) {
        Instant now = Instant.now();

        PurchaseOrderLine line = PurchaseOrderLine.builder()
                .purchaseOrder(order)
                .item(item)
                .orderedQuantity(ordered)
                .receivedQuantity(received)
                .expectedDate(ORDER_DATE.plusDays(14))
                .build();
        line.setCreatedAt(now);
        line.setUpdatedAt(now);
        return entityManager.persistFlushFind(line);
    }
}
