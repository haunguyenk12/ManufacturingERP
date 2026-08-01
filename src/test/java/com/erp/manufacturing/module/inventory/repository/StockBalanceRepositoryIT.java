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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
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
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        InventoryLot lot = InventoryLot.builder().item(item).lotCode("LOT_" + suffix).status(status).build();
        lot.setCreatedAt(now);
        lot.setUpdatedAt(now);
        return entityManager.persistFlushFind(lot);
    }

    private StockBalance persistBalance(Item item, Warehouse warehouse, InventoryLot lot,
            BigDecimal quantity, BigDecimal reservedQuantity) {
        Instant now = Instant.now();

        StockBalance balance = StockBalance.builder().item(item).warehouse(warehouse).lot(lot)
                .quantity(quantity).reservedQuantity(reservedQuantity).build();
        balance.setCreatedAt(now);
        balance.setUpdatedAt(now);
        return entityManager.persistFlushFind(balance);
    }

    @Test
    void aggregateAvailableQuantities_includesNonLotTrackedStock_andExcludesHoldAndRejectedLots() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());

        persistBalance(item, warehouse, null, new BigDecimal("10.000000"), new BigDecimal("2.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE),
                new BigDecimal("5.000000"), new BigDecimal("1.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.HOLD),
                new BigDecimal("100.000000"), BigDecimal.ZERO);
        persistBalance(item, warehouse, persistLot(item, LotStatus.REJECTED),
                new BigDecimal("50.000000"), BigDecimal.ZERO);
        entityManager.clear();

        List<StockAvailabilityProjection> result = repository.aggregateAvailableQuantities(
                List.of(item.getItemId()), List.of(warehouse.getWarehouseId()), LotStatus.AVAILABLE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemId()).isEqualTo(item.getItemId());
        // 12.000000 = (10-2) non-lot-tracked + (5-1) AVAILABLE lot.
        // The HOLD (100) and REJECTED (50) rows are excluded by the status filter.
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo("12.000000");
    }

    @Test
    void aggregatePlanningQuantities_includesNonLotTrackedStock_andExcludesHoldLots() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());

        persistBalance(item, warehouse, null, new BigDecimal("20.000000"), new BigDecimal("5.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.AVAILABLE),
                new BigDecimal("10.000000"), new BigDecimal("2.000000"));
        persistBalance(item, warehouse, persistLot(item, LotStatus.HOLD),
                new BigDecimal("100.000000"), new BigDecimal("50.000000"));
        entityManager.clear();

        List<StockPlanningQuantityProjection> result = repository.aggregatePlanningQuantities(
                List.of(item.getItemId()), List.of(warehouse.getWarehouseId()), LotStatus.AVAILABLE);

        assertThat(result).hasSize(1);
        StockPlanningQuantityProjection projection = result.get(0);
        assertThat(projection.getItemId()).isEqualTo(item.getItemId());
        // Folds the non-lot-tracked row (20/5) into the AVAILABLE-lot row (10/2):
        // onHand=30, reserved=7, available=23. The HOLD row (100/50) is excluded by the status filter.
        assertThat(projection.getOnHandQuantity()).isEqualByComparingTo("30.000000");
        assertThat(projection.getReservedQuantity()).isEqualByComparingTo("7.000000");
        assertThat(projection.getAvailableQuantity()).isEqualByComparingTo("23.000000");
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
                List.of(item.getItemId()), List.of(warehouse.getWarehouseId()), LotStatus.AVAILABLE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemId()).isEqualTo(item.getItemId());
        assertThat(result.get(0).getExcludedLotCount()).isEqualTo(2L);
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
}
