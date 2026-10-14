package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSetting;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSettingStatus;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link ItemWarehouseSettingRepository#search} on a real Postgres (G2-3).
 *
 * <p>The fixture has <b>two companies</b> on purpose. The scope leak this guards (CLAUDE.md §0.46,
 * PF-03) stayed invisible for as long as the database held a single company: an unscoped list then
 * returns exactly the right rows by accident. Only a second company's settings make "filtered by
 * plant" and "not filtered at all" give different answers.
 *
 * <p>Each optional filter is exercised with {@code null} too — a nullable parameter that only appears
 * under {@code is null} is the shape that has broken type inference on Postgres before (§0.36).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ItemWarehouseSettingRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    ItemWarehouseSettingRepository repository;

    @Autowired
    TestEntityManager entityManager;

    private Plant plantA;
    private Plant plantB;
    private Warehouse warehouseA1;
    private Warehouse warehouseA2;
    private Item itemA;
    private ItemWarehouseSetting settingA1;
    private ItemWarehouseSetting settingA2;
    private ItemWarehouseSetting settingB1;

    @BeforeEach
    void setUp() {
        plantA = persistPlant(persistCompany());
        plantB = persistPlant(persistCompany());
        warehouseA1 = persistWarehouse(plantA);
        warehouseA2 = persistWarehouse(plantA);
        Warehouse warehouseB1 = persistWarehouse(plantB);
        itemA = persistItem(plantA.getCompany());
        Item otherItemA = persistItem(plantA.getCompany());
        Item itemB = persistItem(plantB.getCompany());

        settingA1 = persistSetting(itemA, warehouseA1, ItemWarehouseSettingStatus.ACTIVE);
        settingA2 = persistSetting(otherItemA, warehouseA2, ItemWarehouseSettingStatus.ACTIVE);
        settingB1 = persistSetting(itemB, warehouseB1, ItemWarehouseSettingStatus.ACTIVE);
    }

    @Test
    void search_byPlant_returnsOnlyThatPlantsSettings() {
        List<UUID> found = ids(repository.search(plantA.getPlantId(), null, null,
                ItemWarehouseSettingStatus.ACTIVE, page()).getContent());

        assertThat(found).containsExactlyInAnyOrder(settingA1.getSettingId(), settingA2.getSettingId());
    }

    /** The unscoped branch still exists for GLOBAL holders — and spans companies, which is the point. */
    @Test
    void search_withoutAnyScope_spansBothCompanies() {
        List<UUID> found = ids(repository.search(null, null, null,
                ItemWarehouseSettingStatus.ACTIVE, page()).getContent());

        assertThat(found).contains(settingA1.getSettingId(), settingA2.getSettingId(), settingB1.getSettingId());
    }

    @Test
    void search_byPlantAndItem_combinesBothFilters() {
        List<UUID> found = ids(repository.search(plantA.getPlantId(), null, itemA.getItemId(),
                ItemWarehouseSettingStatus.ACTIVE, page()).getContent());

        assertThat(found).containsExactly(settingA1.getSettingId());
    }

    @Test
    void search_byPlantAndWarehouse_combinesBothFilters() {
        List<UUID> found = ids(repository.search(plantA.getPlantId(), warehouseA2.getWarehouseId(), null,
                ItemWarehouseSettingStatus.ACTIVE, page()).getContent());

        assertThat(found).containsExactly(settingA2.getSettingId());
    }

    /** A warehouse of another plant under a plant filter yields nothing, not the warehouse's rows. */
    @Test
    void search_plantAndAWarehouseOfAnotherPlant_returnsNothing() {
        List<UUID> found = ids(repository.search(plantB.getPlantId(), warehouseA1.getWarehouseId(), null,
                ItemWarehouseSettingStatus.ACTIVE, page()).getContent());

        assertThat(found).isEmpty();
    }

    @Test
    void search_withNullStatus_includesInactiveRows() {
        ItemWarehouseSetting inactive = persistSetting(persistItem(plantA.getCompany()), warehouseA1,
                ItemWarehouseSettingStatus.INACTIVE);

        assertThat(ids(repository.search(plantA.getPlantId(), null, null,
                ItemWarehouseSettingStatus.ACTIVE, page()).getContent()))
                .doesNotContain(inactive.getSettingId());
        assertThat(ids(repository.search(plantA.getPlantId(), null, null, null, page()).getContent()))
                .contains(inactive.getSettingId());
    }

    /** The reactivation path picks the most recently touched INACTIVE row of the pair. */
    @Test
    void findFirstInactive_returnsTheMostRecentlyUpdatedInactiveRowOfThePair() {
        Item item = persistItem(plantA.getCompany());
        ItemWarehouseSetting older = persistSetting(item, warehouseA1, ItemWarehouseSettingStatus.INACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"));
        ItemWarehouseSetting newer = persistSetting(item, warehouseA1, ItemWarehouseSettingStatus.INACTIVE,
                Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(repository.findFirstByItemItemIdAndWarehouseWarehouseIdAndStatusOrderByUpdatedAtDesc(
                item.getItemId(), warehouseA1.getWarehouseId(), ItemWarehouseSettingStatus.INACTIVE))
                .map(ItemWarehouseSetting::getSettingId)
                .contains(newer.getSettingId());
        assertThat(older.getSettingId()).isNotEqualTo(newer.getSettingId());
    }

    private static PageRequest page() {
        return PageRequest.of(0, 50);
    }

    private static List<UUID> ids(List<ItemWarehouseSetting> settings) {
        return settings.stream().map(ItemWarehouseSetting::getSettingId).toList();
    }

    private Company persistCompany() {
        String suffix = UUID.randomUUID().toString();
        Company company = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        stamp(company);
        return entityManager.persistFlushFind(company);
    }

    private Plant persistPlant(Company company) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Plant plant = Plant.builder().company(company).code("PL_" + suffix).name("Plant " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        stamp(plant);
        return entityManager.persistFlushFind(plant);
    }

    private Warehouse persistWarehouse(Plant plant) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Warehouse warehouse = Warehouse.builder().plant(plant).code("WH_" + suffix).name("Warehouse " + suffix)
                .type(WarehouseType.RAW_MATERIAL).status(OrganizationStatus.ACTIVE).build();
        stamp(warehouse);
        return entityManager.persistFlushFind(warehouse);
    }

    private Item persistItem(Company company) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Item item = Item.builder().company(company).code("IT_" + suffix).name("Item " + suffix)
                .type(ItemType.RAW_MATERIAL).unit("EA").status(ItemStatus.ACTIVE).build();
        stamp(item);
        return entityManager.persistFlushFind(item);
    }

    private ItemWarehouseSetting persistSetting(Item item, Warehouse warehouse, ItemWarehouseSettingStatus status) {
        return persistSetting(item, warehouse, status, Instant.now());
    }

    private ItemWarehouseSetting persistSetting(Item item, Warehouse warehouse, ItemWarehouseSettingStatus status,
                                                Instant updatedAt) {
        ItemWarehouseSetting setting = ItemWarehouseSetting.builder().item(item).warehouse(warehouse)
                .safetyStock(BigDecimal.ONE).reorderPoint(BigDecimal.ONE).leadTimeDays(1)
                .status(status).build();
        setting.setCreatedAt(updatedAt);
        setting.setUpdatedAt(updatedAt);
        return entityManager.persistFlushFind(setting);
    }

    private void stamp(BaseEntity entity) {
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
    }
}
