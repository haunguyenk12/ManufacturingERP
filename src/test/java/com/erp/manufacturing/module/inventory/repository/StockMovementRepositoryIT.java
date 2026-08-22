package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link StockMovementRepository#findRecentByWarehouseIds} — the dashboard's ledger feed —
 * against a real Postgres Testcontainer.
 *
 * <p>Both properties under test live entirely inside the JPQL, so a mocked repository proves nothing
 * about them (rule R7): the {@code lot} fetch must stay a <b>left</b> join, or every movement of a
 * non-lot-tracked item silently disappears from the feed; and {@code createdAt} alone is not a total
 * order, so rows written in the same transaction would come back in whatever order Postgres picks.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockMovementRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    StockMovementRepository repository;

    @Autowired
    TestEntityManager entityManager;

    // @DataJpaTest does not load JpaAuditingConfig/SecurityAuditorAware, so createdAt (a NOT NULL
    // column) is set explicitly before persisting.

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

    private InventoryLot persistLot(Item item) {
        Instant now = Instant.now();

        InventoryLot lot = InventoryLot.builder().item(item).lotCode("LOT_" + UUID.randomUUID())
                .status(LotStatus.AVAILABLE).receivedAt(now).build();
        lot.setCreatedAt(now);
        lot.setUpdatedAt(now);
        return entityManager.persistFlushFind(lot);
    }

    private StockMovement persistMovement(Item item, Warehouse warehouse, InventoryLot lot, Instant createdAt) {
        return entityManager.persistFlushFind(StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(new BigDecimal("1.000000"))
                .idempotencyKey("IDK_" + UUID.randomUUID())
                .createdAt(createdAt)
                .build());
    }

    @Test
    void findRecentByWarehouseIds_includesMovementsOfNonLotTrackedItems() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        Instant now = Instant.now();

        StockMovement withLot = persistMovement(item, warehouse, persistLot(item), now.minus(1, ChronoUnit.MINUTES));
        StockMovement withoutLot = persistMovement(item, warehouse, null, now);
        entityManager.clear();

        List<StockMovement> result = repository.findRecentByWarehouseIds(
                List.of(warehouse.getWarehouseId()), PageRequest.of(0, 10));

        assertThat(result).extracting(StockMovement::getMovementId)
                .containsExactly(withoutLot.getMovementId(), withLot.getMovementId());
    }

    @Test
    void findRecentByWarehouseIds_returnsNewestFirst_andBreaksTiesOnMovementId() {
        Warehouse warehouse = persistWarehouse();
        Item item = persistItem(warehouse.getPlant().getCompany());
        Instant older = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant sameInstant = Instant.now();

        StockMovement oldest = persistMovement(item, warehouse, null, older);
        StockMovement tieA = persistMovement(item, warehouse, null, sameInstant);
        StockMovement tieB = persistMovement(item, warehouse, null, sameInstant);
        entityManager.clear();

        List<StockMovement> result = repository.findRecentByWarehouseIds(
                List.of(warehouse.getWarehouseId()), PageRequest.of(0, 10));

        boolean aFirst = compareAsPostgresUuid(tieA.getMovementId(), tieB.getMovementId()) > 0;
        UUID expectedFirst = aFirst ? tieA.getMovementId() : tieB.getMovementId();
        UUID expectedSecond = aFirst ? tieB.getMovementId() : tieA.getMovementId();
        assertThat(result).extracting(StockMovement::getMovementId)
                .containsExactly(expectedFirst, expectedSecond, oldest.getMovementId());
    }

    /**
     * Postgres orders {@code uuid} as 16 unsigned bytes, while {@link UUID#compareTo} compares the two
     * halves as <i>signed</i> longs — the two disagree for any pair whose high bit differs. The
     * tiebreaker under test is the database's, so the expectation has to be computed the database's
     * way.
     */
    private static int compareAsPostgresUuid(UUID left, UUID right) {
        int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0
                ? high
                : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    @Test
    void findRecentByWarehouseIds_staysInsideTheRequestedWarehouses() {
        Warehouse inScope = persistWarehouse();
        Warehouse outOfScope = persistWarehouse();
        Item item = persistItem(inScope.getPlant().getCompany());
        Instant now = Instant.now();

        StockMovement wanted = persistMovement(item, inScope, null, now);
        persistMovement(persistItem(outOfScope.getPlant().getCompany()), outOfScope, null, now);
        entityManager.clear();

        List<StockMovement> result = repository.findRecentByWarehouseIds(
                List.of(inScope.getWarehouseId()), PageRequest.of(0, 10));

        assertThat(result).extracting(StockMovement::getMovementId).containsExactly(wanted.getMovementId());
    }
}
