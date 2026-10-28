package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the aggregate availability/planning JPQL in {@link StockBalanceRepository}
 * against a real Postgres Testcontainer, plus a real optimistic-locking conflict on
 * {@link StockBalance} (NEXT_PHASE_PLAN.md T4.4/T4.6, decision D7).
 *
 * <p><b>Regression guard for the implicit-join bug fixed in F1.6.</b> These aggregates used to
 * filter with {@code (b.lot is null or b.lot.status = :availableStatus)}. Dereferencing
 * {@code b.lot.status} as a path expression makes Hibernate emit an INNER JOIN to
 * {@code inventory_lots}, so rows with {@code lot_id IS NULL} (non-lot-tracked items) never
 * satisfied that join and were silently dropped — regardless of the "is null" branch in the
 * JPQL text — under-counting on-hand stock for every non-lot-tracked item in MRP. The queries
 * now use an explicit {@code left join b.lot l}. The assertions below encode invariant B3:
 * non-lot-tracked stock always counts as available, {@code HOLD}/{@code REJECTED} lots never do.
 * Reverting to the implicit join makes both tests fail.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockBalanceRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    StockBalanceRepository repository;

    @Autowired
    TestEntityManager entityManager;

    // @DataJpaTest does not load JpaAuditingConfig/SecurityAuditorAware, so createdAt/updatedAt
    // (NOT NULL columns on BaseEntity subclasses) are set explicitly before persisting.

    private Warehouse persistWarehouse() {
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

        Warehouse warehouse = Warehouse.builder().plant(plant).code("WH_" + suffix).name("Warehouse " + suffix)
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build();
        warehouse.setCreatedAt(now);
        warehouse.setUpdatedAt(now);
        return entityManager.persistFlushFind(warehouse);
    }

    private Item persistItem(Company company) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Item item = Item.builder().company(company).code("ITEM_" + suffix).name("Item " + suffix)
                .type(ItemType.RAW_MATERIAL).unit("EA").lotTracked(true).status(ItemStatus.ACTIVE).build();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return entityManager.persistFlushFind(item);
    }

    private InventoryLot persistLot(Item item, LotStatus status) {
        return persistLot(item, status, "LOT_" + UUID.randomUUID(), null);
    }

    private InventoryLot persistLot(Item item, LotStatus status, String lotCode, Instant expiresAt) {
        Instant now = Instant.now();

        InventoryLot lot = InventoryLot.builder().item(item).lotCode(lotCode).status(status)
                .receivedAt(now).expiresAt(expiresAt).build();
        lot.setCreatedAt(now);
        lot.setUpdatedAt(now);
        return entityManager.persistFlushFind(lot);
    }

    private StockBalance persistBalance(Item item, Warehouse warehouse, InventoryLot lot,
            BigDecimal quantity, BigDecimal reservedQuantity) {
        return persistBalance(item, warehouse, lot, quantity, reservedQuantity, BigDecimal.ZERO);
    }

    private StockBalance persistBalance(Item item, Warehouse warehouse, InventoryLot lot,
            BigDecimal quantity, BigDecimal reservedQuantity, BigDecimal qualityHoldQuantity) {
        Instant now = Instant.now();

        StockBalance balance = StockBalance.builder().item(item).warehouse(warehouse).lot(lot)
                .quantity(quantity).reservedQuantity(reservedQuantity)
                .qualityHoldQuantity(qualityHoldQuantity).build();
        balance.setCreatedAt(now);
        balance.setUpdatedAt(now);
        return entityManager.persistFlushFind(balance);
    }

    /**
     * ISS-09 / DEC-02: one row per {@code Item + Warehouse}, with every bucket the dashboard shows.
     * The whole point of the endpoint is that the caller stops deriving lot status from
     * {@code availableQuantity > 0}, so the buckets have to disagree with each other here — a
     * fixture where held stock happened to be zero would pass against a query that ignored status.
     */
    @Test
    void aggregateBalances_splitsOnHandIntoAvailableHeldRejectedAndExpiredBuckets() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());

        persistBalance(item, warehouse, null, new BigDecimal("10.000000"),
                new BigDecimal("2.000000"), new BigDecimal("3.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE),
                new BigDecimal("5.000000"), new BigDecimal("1.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.HOLD),
                new BigDecimal("100.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.REJECTED),
                new BigDecimal("50.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.EXPIRED),
                new BigDecimal("7.000000"), BigDecimal.ZERO);
        entityManager.clear();

        var page = repository.aggregateBalances(warehouse.getWarehouseId(), null, null, Instant.now(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        StockBalanceAggregateProjection row = page.getContent().get(0);
        assertThat(row.getItemId()).isEqualTo(item.getItemId());
        assertThat(row.getItemCode()).isEqualTo(item.getCode());
        assertThat(row.getUom()).isEqualTo("EA");
        // On hand keeps the physical truth: 10 + 5 + 100 + 50 + 7.
        assertThat(row.getOnHandQuantity()).isEqualByComparingTo("172.000000");
        assertThat(row.getReservedQuantity()).isEqualByComparingTo("3.000000");
        // Available counts only the untracked row and the AVAILABLE lot: (10-2-3) + (5-1).
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("9.000000");
        assertThat(row.getQualityHoldQuantity()).isEqualByComparingTo("3.000000");
        // G4: the HOLD lot gets its own bucket instead of vanishing from every bucket.
        assertThat(row.getHeldLotQuantity()).isEqualByComparingTo("100.000000");
        assertThat(row.getRejectedQuantity()).isEqualByComparingTo("50.000000");
        assertThat(row.getExpiredQuantity()).isEqualByComparingTo("7.000000");
        assertThat(row.getLotCount()).isEqualTo(4);
        assertThat(row.getUpdatedAt()).isNotNull();
    }

    /**
     * G4 (user decision 2026-10-15): an AVAILABLE lot whose {@code expiresAt} has passed is counted as
     * expired, not available — the manual issue refuses it, so offering it as available would be the
     * "one system, two numbers" defect of CLAUDE.md §0.44 again. A lot expiring later stays available.
     */
    @Test
    void aggregateBalances_availableLotPastItsExpiry_isExpiredNotAvailable() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        Instant now = Instant.parse("2026-10-15T00:00:00Z");
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE, "PAST_" + UUID.randomUUID(),
                now.minusSeconds(1)), new BigDecimal("4.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE, "LATER_" + UUID.randomUUID(),
                now.plusSeconds(86_400)), new BigDecimal("6.000000"), BigDecimal.ZERO);
        entityManager.clear();

        StockBalanceAggregateProjection row = repository.aggregateBalances(
                warehouse.getWarehouseId(), null, null, now, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(row.getOnHandQuantity()).isEqualByComparingTo("10.000000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("6.000000");
        assertThat(row.getExpiredQuantity()).isEqualByComparingTo("4.000000");
        assertThat(row.getHeldLotQuantity()).isEqualByComparingTo("0");
    }

    /**
     * The {@code left join b.lot l} is load-bearing, not tidiness: an inner join would drop every
     * balance of a non-lot-tracked item, which is most of the ledger (same trap as CLAUDE.md §0.43).
     */
    @Test
    void aggregateBalances_reportsItemsThatHaveNoLotsAtAll() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());

        persistBalance(item, warehouse, null, new BigDecimal("12.000000"), new BigDecimal("2.000000"));
        entityManager.clear();

        var page = repository.aggregateBalances(warehouse.getWarehouseId(), item.getItemId(), null, Instant.now(),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getOnHandQuantity()).isEqualByComparingTo("12.000000");
        assertThat(page.getContent().get(0).getAvailableQuantity()).isEqualByComparingTo("10.000000");
        assertThat(page.getContent().get(0).getLotCount()).isZero();
    }

    /** One row per item — several lots of the same item must collapse, not paginate separately. */
    @Test
    void aggregateBalances_returnsOneRowPerItemAndHonoursTheItemFilter() {
        Warehouse warehouse = persistWarehouse();
        Item first = persistItem(warehouse.getPlant().getCompany());
        Item second = persistItem(warehouse.getPlant().getCompany());

        persistBalance(first, warehouse, persistLot(first, LotStatus.AVAILABLE),
                new BigDecimal("4.000000"), BigDecimal.ZERO);
        persistBalance(first, warehouse, persistLot(first, LotStatus.AVAILABLE),
                new BigDecimal("6.000000"), BigDecimal.ZERO);
        persistBalance(second, warehouse, null, new BigDecimal("8.000000"), BigDecimal.ZERO);
        entityManager.clear();

        var all = repository.aggregateBalances(warehouse.getWarehouseId(), null, null, Instant.now(), PageRequest.of(0, 20));
        var filtered = repository.aggregateBalances(warehouse.getWarehouseId(), first.getItemId(), null, Instant.now(),
                PageRequest.of(0, 20));

        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(filtered.getContent().get(0).getOnHandQuantity()).isEqualByComparingTo("10.000000");
        assertThat(filtered.getContent().get(0).getLotCount()).isEqualTo(2);
    }

    @Test
    void aggregateAvailableQuantities_includesNonLotTrackedStock_andExcludesHoldAndRejectedLots() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());

        persistBalance(item, warehouse, null, new BigDecimal("10.000000"),
                new BigDecimal("2.000000"), new BigDecimal("3.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE),
                new BigDecimal("5.000000"), new BigDecimal("1.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.HOLD),
                new BigDecimal("100.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.REJECTED),
                new BigDecimal("50.000000"), BigDecimal.ZERO);
        entityManager.clear();

        List<StockAvailabilityProjection> result = repository.aggregateAvailableQuantities(
                List.of(item.getItemId()), List.of(warehouse.getWarehouseId()), LotStatus.AVAILABLE, Instant.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemId()).isEqualTo(item.getItemId());
        // 9.000000 = (10-2 reserved-3 quality hold) non-lot-tracked + (5-1) AVAILABLE lot.
        // The HOLD (100) and REJECTED (50) rows are excluded by the status filter.
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo("9.000000");
    }

    @Test
    void aggregatePlanningQuantities_includesNonLotTrackedStock_andExcludesHoldLots() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());

        persistBalance(item, warehouse, null, new BigDecimal("20.000000"),
                new BigDecimal("5.000000"), new BigDecimal("6.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE),
                new BigDecimal("10.000000"), new BigDecimal("2.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.HOLD),
                new BigDecimal("100.000000"), new BigDecimal("50.000000"));
        entityManager.clear();

        List<StockPlanningQuantityProjection> result = repository.aggregatePlanningQuantities(
                List.of(item.getItemId()), List.of(warehouse.getWarehouseId()), LotStatus.AVAILABLE, Instant.now());

        assertThat(result).hasSize(1);
        StockPlanningQuantityProjection projection = result.get(0);
        assertThat(projection.getItemId()).isEqualTo(item.getItemId());
        // Folds the non-lot-tracked row (20/5 reserved/6 quality hold) into the AVAILABLE-lot row
        // (10/2): onHand=30, reserved=7, available=17. The HOLD lot is excluded by status.
        assertThat(projection.getOnHandQuantity()).isEqualByComparingTo("30.000000");
        assertThat(projection.getReservedQuantity()).isEqualByComparingTo("7.000000");
        assertThat(projection.getAvailableQuantity()).isEqualByComparingTo("17.000000");
    }

    /**
     * The dashboard reads this one: same eligibility rule as the other aggregates, but split per
     * warehouse and carrying the three terms behind {@code availableQuantity}. Splitting is the part
     * a mock cannot check — the alert lines of two warehouses must not be folded into one figure.
     */
    @Test
    void aggregateStockQuantitiesByWarehouse_splitsPerWarehouse_andKeepsTheTermsBehindAvailability() {
        Warehouse warehouseA = persistWarehouse();
        Warehouse warehouseB = persistWarehouse();
        Item item = persistItem(warehouseA.getPlant().getCompany());

        persistBalance(item, warehouseA, null, new BigDecimal("20.000000"),
                new BigDecimal("5.000000"), new BigDecimal("6.000000"));
        persistBalance(item, warehouseA, persistLot(item, LotStatus.AVAILABLE),
                new BigDecimal("10.000000"), new BigDecimal("2.000000"));
        persistBalance(item, warehouseA, persistLot(item, LotStatus.HOLD),
                new BigDecimal("100.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouseA, persistLot(item, LotStatus.REJECTED),
                new BigDecimal("50.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouseB, null, new BigDecimal("7.000000"), BigDecimal.ZERO);
        entityManager.clear();

        List<StockQuantityByWarehouseProjection> result = repository.aggregateStockQuantitiesByWarehouse(
                List.of(item.getItemId()),
                List.of(warehouseA.getWarehouseId(), warehouseB.getWarehouseId()),
                LotStatus.AVAILABLE, Instant.now());

        assertThat(result).hasSize(2);
        StockQuantityByWarehouseProjection a = result.stream()
                .filter(row -> row.getWarehouseId().equals(warehouseA.getWarehouseId()))
                .findFirst().orElseThrow();
        // 20/5/6 non-lot-tracked + 10/2 AVAILABLE lot; the HOLD (100) and REJECTED (50) lots
        // contribute nothing at all, not even to on-hand — neither is stock the dashboard may offer.
        assertThat(a.getOnHandQuantity()).isEqualByComparingTo("30.000000");
        assertThat(a.getReservedQuantity()).isEqualByComparingTo("7.000000");
        assertThat(a.getQualityHoldQuantity()).isEqualByComparingTo("6.000000");
        assertThat(a.getAvailableQuantity()).isEqualByComparingTo("17.000000");

        StockQuantityByWarehouseProjection b = result.stream()
                .filter(row -> row.getWarehouseId().equals(warehouseB.getWarehouseId()))
                .findFirst().orElseThrow();
        assertThat(b.getAvailableQuantity()).isEqualByComparingTo("7.000000");
    }

    @Test
    void aggregateExcludedLotCounts_countsOnlyNonAvailableLotsHoldingStock() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());

        // Neither the non-lot-tracked row nor the AVAILABLE lot can be "excluded", and an empty
        // HOLD lot is not stock a planner could recover — only the two stocked HOLD/REJECTED lots count.
        persistBalance(item, warehouse, null, new BigDecimal("20.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE),
                new BigDecimal("10.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.HOLD),
                new BigDecimal("100.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.REJECTED),
                new BigDecimal("50.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.HOLD), BigDecimal.ZERO, BigDecimal.ZERO);
        entityManager.clear();

        List<StockExcludedLotCountProjection> result = repository.aggregateExcludedLotCounts(
                List.of(item.getItemId()), List.of(warehouse.getWarehouseId()), LotStatus.AVAILABLE, Instant.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemId()).isEqualTo(item.getItemId());
        assertThat(result.get(0).getExcludedLotCount()).isEqualTo(2L);
    }

    // ── CF-2 (B264): an AVAILABLE lot past its expiresAt is not available to planning / work orders ────────────

    /**
     * Two warehouses in two plants (persistWarehouse makes a company + plant each). In warehouse A: undated non-lot stock,
     * an AVAILABLE lot expiring exactly at {@code now} (boundary, excluded), one expiring {@code now + 1s} (included),
     * an AVAILABLE lot with no expiry (included), a HOLD lot that is not expired (excluded) and one expired yesterday
     * (excluded). In warehouse B: an expired lot and a future one. Reserved quantities on the excluded rows are large on
     * purpose so a query that let them in would move every figure.
     */
    private record ExpiryFixture(Item item, Warehouse warehouseA, Warehouse warehouseB, Instant now,
                                 StockBalance nonLot, StockBalance atNow, StockBalance inOneSecond,
                                 StockBalance undatedLot, StockBalance holdFuture, StockBalance expiredYesterday,
                                 StockBalance expiredInB, StockBalance futureInB) {}

    private ExpiryFixture persistExpiryFixture() {
        Warehouse warehouseA = persistWarehouse();
        Warehouse warehouseB = persistWarehouse();
        Item item = persistItem(warehouseA.getPlant().getCompany());
        // Postgres keeps microseconds; truncate so "expiresAt == now" is an exact equality in SQL.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        StockBalance nonLot = persistBalance(item, warehouseA, null, new BigDecimal("4.000000"), BigDecimal.ZERO);
        StockBalance atNow = persistBalance(item, warehouseA,
                persistLot(item, LotStatus.AVAILABLE, "LOT_NOW_" + UUID.randomUUID(), now),
                new BigDecimal("100.000000"), new BigDecimal("40.000000"));
        StockBalance inOneSecond = persistBalance(item, warehouseA,
                persistLot(item, LotStatus.AVAILABLE, "LOT_1S_" + UUID.randomUUID(), now.plusSeconds(1)),
                new BigDecimal("7.000000"), new BigDecimal("2.000000"));
        StockBalance undatedLot = persistBalance(item, warehouseA,
                persistLot(item, LotStatus.AVAILABLE, "LOT_UNDATED_" + UUID.randomUUID(), null),
                new BigDecimal("5.000000"), BigDecimal.ZERO);
        StockBalance holdFuture = persistBalance(item, warehouseA,
                persistLot(item, LotStatus.HOLD, "LOT_HOLD_" + UUID.randomUUID(), now.plus(1, ChronoUnit.DAYS)),
                new BigDecimal("50.000000"), BigDecimal.ZERO);
        StockBalance expiredYesterday = persistBalance(item, warehouseA,
                persistLot(item, LotStatus.AVAILABLE, "LOT_OLD_" + UUID.randomUUID(), now.minus(1, ChronoUnit.DAYS)),
                new BigDecimal("30.000000"), new BigDecimal("10.000000"));
        StockBalance expiredInB = persistBalance(item, warehouseB,
                persistLot(item, LotStatus.AVAILABLE, "LOT_B_OLD_" + UUID.randomUUID(), now.minus(1, ChronoUnit.HOURS)),
                new BigDecimal("200.000000"), BigDecimal.ZERO);
        StockBalance futureInB = persistBalance(item, warehouseB,
                persistLot(item, LotStatus.AVAILABLE, "LOT_B_NEW_" + UUID.randomUUID(), now.plus(1, ChronoUnit.DAYS)),
                new BigDecimal("3.000000"), BigDecimal.ZERO);
        entityManager.clear();
        return new ExpiryFixture(item, warehouseA, warehouseB, now, nonLot, atNow, inOneSecond, undatedLot, holdFuture,
                expiredYesterday, expiredInB, futureInB);
    }

    @Test
    void aggregateAvailableQuantities_cf2_excludesLotsExpiredAtOrBeforeNow_keepsFutureUndatedAndNonLotStock() {
        ExpiryFixture f = persistExpiryFixture();

        List<StockAvailabilityProjection> onlyA = repository.aggregateAvailableQuantities(
                List.of(f.item().getItemId()), List.of(f.warehouseA().getWarehouseId()), LotStatus.AVAILABLE, f.now());
        List<StockAvailabilityProjection> both = repository.aggregateAvailableQuantities(
                List.of(f.item().getItemId()),
                List.of(f.warehouseA().getWarehouseId(), f.warehouseB().getWarehouseId()), LotStatus.AVAILABLE, f.now());

        // A: 4 non-lot + (7 - 2) lot expiring in 1s + 5 undated lot = 14. The lot expiring exactly at now, the
        // expired one and the HOLD one add nothing.
        assertThat(onlyA).singleElement().satisfies(row -> assertThat(row.getQuantity()).isEqualByComparingTo("14"));
        // + B: 3 from the future lot, the expired 200 stays out.
        assertThat(both).singleElement().satisfies(row -> assertThat(row.getQuantity()).isEqualByComparingTo("17"));
    }

    @Test
    void aggregatePlanningQuantities_cf2_treatsAnExpiredLotLikeAHeldOne_onHandReservedAndAvailableAllLeaveIt() {
        ExpiryFixture f = persistExpiryFixture();

        List<StockPlanningQuantityProjection> result = repository.aggregatePlanningQuantities(
                List.of(f.item().getItemId()),
                List.of(f.warehouseA().getWarehouseId(), f.warehouseB().getWarehouseId()), LotStatus.AVAILABLE, f.now());

        assertThat(result).singleElement().satisfies(row -> {
            // 4 + 7 + 5 + 3; the 100 at-now, 30 yesterday, 200 in B and the 50 HOLD are not counted.
            assertThat(row.getOnHandQuantity()).isEqualByComparingTo("19");
            // only the 2 on the lot expiring in 1s; the 40 / 10 reserved on expired lots are not.
            assertThat(row.getReservedQuantity()).isEqualByComparingTo("2");
            assertThat(row.getAvailableQuantity()).isEqualByComparingTo("17");
        });
    }

    @Test
    void aggregateStockQuantitiesByWarehouse_cf2_dropsExpiredLotsPerWarehouse() {
        ExpiryFixture f = persistExpiryFixture();

        List<StockQuantityByWarehouseProjection> result = repository.aggregateStockQuantitiesByWarehouse(
                List.of(f.item().getItemId()),
                List.of(f.warehouseA().getWarehouseId(), f.warehouseB().getWarehouseId()), LotStatus.AVAILABLE, f.now());

        assertThat(result).hasSize(2);
        StockQuantityByWarehouseProjection a = result.stream()
                .filter(row -> row.getWarehouseId().equals(f.warehouseA().getWarehouseId())).findFirst().orElseThrow();
        StockQuantityByWarehouseProjection b = result.stream()
                .filter(row -> row.getWarehouseId().equals(f.warehouseB().getWarehouseId())).findFirst().orElseThrow();
        assertThat(a.getOnHandQuantity()).isEqualByComparingTo("16");
        assertThat(a.getReservedQuantity()).isEqualByComparingTo("2");
        assertThat(a.getAvailableQuantity()).isEqualByComparingTo("14");
        assertThat(b.getOnHandQuantity()).isEqualByComparingTo("3");
        assertThat(b.getAvailableQuantity()).isEqualByComparingTo("3");
    }

    @Test
    void aggregateExcludedLotCounts_cf2_countsExpiredAvailableLotsAsWellAsNonAvailableOnes() {
        ExpiryFixture f = persistExpiryFixture();
        // An expired lot that is already empty is not stock a planner could recover — still not counted.
        Warehouse warehouseA = entityManager.find(Warehouse.class, f.warehouseA().getWarehouseId());
        Item item = entityManager.find(Item.class, f.item().getItemId());
        persistBalance(item, warehouseA,
                persistLot(item, LotStatus.AVAILABLE, "LOT_EMPTY_" + UUID.randomUUID(), f.now().minusSeconds(5)),
                BigDecimal.ZERO, BigDecimal.ZERO);
        entityManager.clear();

        List<StockExcludedLotCountProjection> onlyA = repository.aggregateExcludedLotCounts(
                List.of(f.item().getItemId()), List.of(f.warehouseA().getWarehouseId()), LotStatus.AVAILABLE, f.now());
        List<StockExcludedLotCountProjection> both = repository.aggregateExcludedLotCounts(
                List.of(f.item().getItemId()),
                List.of(f.warehouseA().getWarehouseId(), f.warehouseB().getWarehouseId()), LotStatus.AVAILABLE, f.now());

        // A: at-now (boundary), yesterday, HOLD-future = 3. The 1s lot, undated lot and non-lot stock are not excluded.
        assertThat(onlyA).singleElement().satisfies(row -> assertThat(row.getExcludedLotCount()).isEqualTo(3L));
        // + the expired lot in B = 4.
        assertThat(both).singleElement().satisfies(row -> assertThat(row.getExcludedLotCount()).isEqualTo(4L));
    }

    @Test
    void findIssuableBalancesFefo_cf2_skipsExpiredAndHeldLots_keepsFefoOrderWithUndatedStockLast() {
        ExpiryFixture f = persistExpiryFixture();

        List<StockBalance> result = repository.findIssuableBalancesFefo(f.item().getItemId(),
                List.of(f.warehouseA().getWarehouseId(), f.warehouseB().getWarehouseId()), LotStatus.AVAILABLE, f.now());

        List<UUID> ids = result.stream().map(StockBalance::getBalanceId).toList();
        assertThat(ids).containsExactlyInAnyOrder(f.inOneSecond().getBalanceId(), f.futureInB().getBalanceId(),
                f.undatedLot().getBalanceId(), f.nonLot().getBalanceId());
        // FEFO: the lot expiring in 1s, then the one expiring tomorrow; undated stock after both.
        assertThat(ids.subList(0, 2)).containsExactly(f.inOneSecond().getBalanceId(), f.futureInB().getBalanceId());
    }

    /**
     * FE fixture (CAPSTONE3 follow-up): the item needs 10; lot A has 10 AVAILABLE but expired at T, lot B has 6
     * expiring at T + 1 day. Planning sees 6 on hand and 6 available, counts one excluded lot, and FEFO offers lot B
     * only — so automatic reservation can cover at most 6, never from lot A.
     */
    @Test
    void feFixture_expiredLotA_freshLotB_planningSeesSix_excludesOne_fefoOffersOnlyLotB() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        Instant t = Instant.now().truncatedTo(ChronoUnit.MICROS);
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE, "FE_LOT_A_" + UUID.randomUUID(), t),
                new BigDecimal("10.000000"), BigDecimal.ZERO);
        StockBalance lotB = persistBalance(item, warehouse,
                persistLot(item, LotStatus.AVAILABLE, "FE_LOT_B_" + UUID.randomUUID(), t.plus(1, ChronoUnit.DAYS)),
                new BigDecimal("6.000000"), BigDecimal.ZERO);
        entityManager.clear();
        List<UUID> items = List.of(item.getItemId());
        List<UUID> warehouses = List.of(warehouse.getWarehouseId());

        assertThat(repository.aggregatePlanningQuantities(items, warehouses, LotStatus.AVAILABLE, t))
                .singleElement().satisfies(row -> {
                    assertThat(row.getOnHandQuantity()).isEqualByComparingTo("6");
                    assertThat(row.getAvailableQuantity()).isEqualByComparingTo("6");
                });
        assertThat(repository.aggregateExcludedLotCounts(items, warehouses, LotStatus.AVAILABLE, t))
                .singleElement().satisfies(row -> assertThat(row.getExcludedLotCount()).isEqualTo(1L));
        assertThat(repository.aggregateAvailableQuantities(items, warehouses, LotStatus.AVAILABLE, t))
                .singleElement().satisfies(row -> assertThat(row.getQuantity()).isEqualByComparingTo("6"));
        assertThat(repository.findIssuableBalancesFefo(item.getItemId(), warehouses, LotStatus.AVAILABLE, t))
                .extracting(StockBalance::getBalanceId).containsExactly(lotB.getBalanceId());
    }

    @Test
    void concurrentUpdate_staleVersion_throwsOptimisticLockException() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        StockBalance saved = persistBalance(item, warehouse, null, BigDecimal.TEN, BigDecimal.ZERO);
        UUID balanceId = saved.getBalanceId();
        entityManager.clear();

        // copy1 is detached right after loading so it keeps a stale (version=0) in-memory
        // snapshot even after copy2's update bumps the row's real version in the database.
        StockBalance copy1 = repository.findById(balanceId).orElseThrow();
        entityManager.detach(copy1);

        StockBalance copy2 = repository.findById(balanceId).orElseThrow();
        copy2.setQuantity(new BigDecimal("20.000000"));
        repository.saveAndFlush(copy2);
        entityManager.clear();

        copy1.setQuantity(new BigDecimal("30.000000"));
        assertThatThrownBy(() -> repository.saveAndFlush(copy1))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    /**
     * C2-2. Every filter here defaults to {@code null} in the common case (no filter applied), which
     * is exactly the shape that has twice broken this query family at parse time rather than just
     * mis-filtering ({@code lower(bytea)}, {@code CLAUDE.md §0.24}/{@code §0.36}) — this "no filters"
     * case is the regression guard for that, not just a happy-path smoke test.
     */
    @Test
    void searchLots_withoutFilters_returnsEveryLotInTheWarehouse() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE), BigDecimal.TEN, BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.HOLD), BigDecimal.ONE, BigDecimal.ZERO);
        // Non-lot-tracked balance must not appear in a lot listing.
        persistBalance(item, warehouse, null, BigDecimal.TEN, BigDecimal.ZERO);
        entityManager.clear();

        var result = repository.searchLots(warehouse.getWarehouseId(), null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void searchLots_filtersByItemId() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Item wanted = persistItem(company);
        Item other = persistItem(company);
        persistBalance(wanted, warehouse, persistLot(wanted, LotStatus.AVAILABLE), BigDecimal.ONE, BigDecimal.ZERO);
        persistBalance(other, warehouse, persistLot(other, LotStatus.AVAILABLE), BigDecimal.ONE, BigDecimal.ZERO);
        entityManager.clear();

        var result = repository.searchLots(warehouse.getWarehouseId(), wanted.getItemId(), null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(b -> b.getItem().getItemId())
                .containsExactly(wanted.getItemId());
    }

    @Test
    void searchLots_filtersByStatus() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        InventoryLot wanted = persistLot(item, LotStatus.HOLD);
        persistBalance(item, warehouse, wanted, BigDecimal.ONE, BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE), BigDecimal.ONE, BigDecimal.ZERO);
        entityManager.clear();

        var result = repository.searchLots(warehouse.getWarehouseId(), null, LotStatus.HOLD, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(b -> b.getLot().getLotId()).containsExactly(wanted.getLotId());
    }

    @Test
    void searchLots_filtersBySearchOnLotCodeCaseInsensitivePartialMatch() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        InventoryLot wanted = persistLot(item, LotStatus.AVAILABLE, "LOT-ABC-123", null);
        persistBalance(item, warehouse, wanted, BigDecimal.ONE, BigDecimal.ZERO);
        InventoryLot other = persistLot(item, LotStatus.AVAILABLE, "LOT-XYZ-999", null);
        persistBalance(item, warehouse, other, BigDecimal.ONE, BigDecimal.ZERO);
        entityManager.clear();

        var result = repository.searchLots(warehouse.getWarehouseId(), null, null, "abc", null, null,
                PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(b -> b.getLot().getLotId()).containsExactly(wanted.getLotId());
    }

    @Test
    void searchLots_filtersByExpiryRange() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        Instant now = Instant.now();
        InventoryLot tooSoon = persistLot(item, LotStatus.AVAILABLE, "LOT_" + UUID.randomUUID(),
                now.plus(1, ChronoUnit.DAYS));
        InventoryLot inRange = persistLot(item, LotStatus.AVAILABLE, "LOT_" + UUID.randomUUID(),
                now.plus(10, ChronoUnit.DAYS));
        InventoryLot tooLate = persistLot(item, LotStatus.AVAILABLE, "LOT_" + UUID.randomUUID(),
                now.plus(100, ChronoUnit.DAYS));
        persistBalance(item, warehouse, tooSoon, BigDecimal.ONE, BigDecimal.ZERO);
        persistBalance(item, warehouse, inRange, BigDecimal.ONE, BigDecimal.ZERO);
        persistBalance(item, warehouse, tooLate, BigDecimal.ONE, BigDecimal.ZERO);
        entityManager.clear();

        var result = repository.searchLots(warehouse.getWarehouseId(), null, null, null,
                now.plus(5, ChronoUnit.DAYS), now.plus(20, ChronoUnit.DAYS), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(b -> b.getLot().getLotId()).containsExactly(inRange.getLotId());
    }

    @Test
    void searchLots_combinesItemAndStatusFilters() {
        Warehouse warehouse = persistWarehouse();
        Company company = warehouse.getPlant().getCompany();
        Item item = persistItem(company);
        InventoryLot wanted = persistLot(item, LotStatus.AVAILABLE);
        persistBalance(item, warehouse, wanted, BigDecimal.ONE, BigDecimal.ZERO);
        // Same item, different status — must be excluded once status is added to the filter set.
        persistBalance(item, warehouse, persistLot(item, LotStatus.HOLD), BigDecimal.ONE, BigDecimal.ZERO);
        entityManager.clear();

        var result = repository.searchLots(warehouse.getWarehouseId(), item.getItemId(), LotStatus.AVAILABLE,
                null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(b -> b.getLot().getLotId()).containsExactly(wanted.getLotId());
    }

    /**
     * {@code uk_stock_balances_item_warehouse_lot} is unique per {@code (item, warehouse, lot)}, not
     * per {@code (item, lot)} — a lot can legitimately hold stock in more than one warehouse.
     */
    @Test
    void findByLotLotId_returnsAllWarehouseRowsForALotThatSpansTwoWarehouses() {
        Warehouse warehouseA = persistWarehouse();
        Warehouse warehouseB = persistWarehouse();
        Item item = persistItem(warehouseA.getPlant().getCompany());
        InventoryLot lot = persistLot(item, LotStatus.AVAILABLE);
        persistBalance(item, warehouseA, lot, new BigDecimal("5"), BigDecimal.ZERO);
        persistBalance(item, warehouseB, lot, new BigDecimal("7"), BigDecimal.ZERO);
        entityManager.clear();

        List<StockBalance> result = repository.findByLotLotId(lot.getLotId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(b -> b.getWarehouse().getWarehouseId())
                .containsExactlyInAnyOrder(warehouseA.getWarehouseId(), warehouseB.getWarehouseId());
    }

    /**
     * G4-1 (B149): the lot-status guard asks for every warehouse that still holds the lot. The fixture
     * spreads one lot over two warehouses of two different plants — the shape a single-warehouse check
     * misses — and leaves a third position at zero, which must not demand access it does not affect.
     */
    @Test
    void findWarehouseIdsHoldingLot_returnsEveryWarehouseWithStock_andSkipsEmptyPositions() {
        Warehouse first = persistWarehouse();
        Warehouse second = persistWarehouse();
        Warehouse emptied = persistWarehouse();
        Item item = persistItem(first.getPlant().getCompany());
        InventoryLot lot = persistLot(item, LotStatus.AVAILABLE);
        InventoryLot otherLot = persistLot(item, LotStatus.AVAILABLE);
        persistBalance(item, first, lot, new BigDecimal("5"), BigDecimal.ZERO);
        persistBalance(item, second, lot, new BigDecimal("3"), BigDecimal.ZERO);
        persistBalance(item, emptied, lot, BigDecimal.ZERO, BigDecimal.ZERO);
        persistBalance(item, emptied, otherLot, new BigDecimal("9"), BigDecimal.ZERO);
        entityManager.clear();

        assertThat(repository.findWarehouseIdsHoldingLot(lot.getLotId()))
                .containsExactlyInAnyOrder(first.getWarehouseId(), second.getWarehouseId());
    }

    /** G4-4: {@code search} filters on item code or name, case-insensitively; {@code null} keeps every row. */
    @Test
    void aggregateBalances_searchMatchesCodeOrName_andNullKeepsEveryRow() {
        Warehouse warehouse = persistWarehouse();
        Item first = persistItem(warehouse.getPlant().getCompany());
        Item second = persistItem(warehouse.getPlant().getCompany());
        persistBalance(first, warehouse, null, BigDecimal.ONE, BigDecimal.ZERO);
        persistBalance(second, warehouse, null, BigDecimal.ONE, BigDecimal.ZERO);
        entityManager.clear();
        String fragment = first.getCode().substring(5, 15).toLowerCase();

        var all = repository.aggregateBalances(warehouse.getWarehouseId(), null, null, Instant.now(), PageRequest.of(0, 20));
        var byCode = repository.aggregateBalances(warehouse.getWarehouseId(), null, fragment, Instant.now(), PageRequest.of(0, 20));
        var byName = repository.aggregateBalances(warehouse.getWarehouseId(), null,
                second.getName().toUpperCase(), Instant.now(), PageRequest.of(0, 20));

        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(byCode.getTotalElements()).isEqualTo(1);
        assertThat(byCode.getContent().get(0).getItemId()).isEqualTo(first.getItemId());
        assertThat(byName.getContent()).singleElement()
                .satisfies(row -> assertThat(row.getItemId()).isEqualTo(second.getItemId()));
    }

    /** G4-3: the count lock reads one exact position — the lot-less row is not the lot row. */
    @Test
    void lockPosition_withAndWithoutLot_selectExactlyThatPosition() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        InventoryLot lot = persistLot(item, LotStatus.AVAILABLE);
        persistBalance(item, warehouse, null, new BigDecimal("2"), BigDecimal.ZERO);
        persistBalance(item, warehouse, lot, new BigDecimal("7"), BigDecimal.ZERO);
        entityManager.clear();

        assertThat(repository.lockPositionWithoutLot(item.getItemId(), warehouse.getWarehouseId()))
                .get().satisfies(b -> assertThat(b.getQuantity()).isEqualByComparingTo("2"));
        assertThat(repository.lockPositionWithLot(item.getItemId(), warehouse.getWarehouseId(), lot.getLotId()))
                .get().satisfies(b -> assertThat(b.getQuantity()).isEqualByComparingTo("7"));
        assertThat(repository.existsByLotLotIdAndReservedQuantityGreaterThan(lot.getLotId(), BigDecimal.ZERO)).isFalse();
    }
}
