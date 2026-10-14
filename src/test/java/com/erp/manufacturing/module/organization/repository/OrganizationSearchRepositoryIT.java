package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.audit.BaseEntity;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link PlantRepository#search} and {@link WarehouseRepository#search} on a real Postgres (G2-4).
 *
 * <p>Both queries take a nullable {@code String} into {@code concat}/{@code like} and a nullable enum
 * under {@code is null} — the two shapes that have failed type inference on Postgres before
 * (CLAUDE.md §0.24, §0.36) while every mocked test stayed green. Each case seeds a row that must
 * <em>not</em> match next to the one that must.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrganizationSearchRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired TestEntityManager entityManager;

    @Test
    void plantSearch_withoutFilters_returnsEveryPlantOfTheCompanyOnly() {
        Company company = persistCompany();
        Plant first = persistPlant(company, "PL-HN", "Hanoi Plant", OrganizationStatus.ACTIVE);
        Plant second = persistPlant(company, "PL-SG", "Saigon Plant", OrganizationStatus.INACTIVE);
        persistPlant(persistCompany(), "PL-DN", "Danang Plant", OrganizationStatus.ACTIVE);

        assertThat(plantRepository.search(company.getCompanyId(), null, null, page()).getContent())
                .extracting(Plant::getPlantId)
                .containsExactlyInAnyOrder(first.getPlantId(), second.getPlantId());
    }

    @Test
    void plantSearch_matchesCodeOrNameCaseInsensitively() {
        Company company = persistCompany();
        Plant byCode = persistPlant(company, "PL-HANOI", "Nothing", OrganizationStatus.ACTIVE);
        Plant byName = persistPlant(company, "PL-X", "Hanoi Annex", OrganizationStatus.ACTIVE);
        persistPlant(company, "PL-SG", "Saigon Plant", OrganizationStatus.ACTIVE);

        assertThat(plantRepository.search(company.getCompanyId(), null, "hanoi", page()).getContent())
                .extracting(Plant::getPlantId)
                .containsExactlyInAnyOrder(byCode.getPlantId(), byName.getPlantId());
    }

    @Test
    void plantSearch_statusFilterWithAndWithoutSearch() {
        Company company = persistCompany();
        Plant active = persistPlant(company, "PL-HN", "Hanoi Plant", OrganizationStatus.ACTIVE);
        Plant inactive = persistPlant(company, "PL-HN2", "Hanoi Old", OrganizationStatus.INACTIVE);

        assertThat(plantRepository.search(company.getCompanyId(), OrganizationStatus.INACTIVE, null, page())
                .getContent()).extracting(Plant::getPlantId).containsExactly(inactive.getPlantId());
        assertThat(plantRepository.search(company.getCompanyId(), OrganizationStatus.ACTIVE, "hanoi", page())
                .getContent()).extracting(Plant::getPlantId).containsExactly(active.getPlantId());
    }

    @Test
    void warehouseSearch_withoutFilters_returnsEveryWarehouseOfThePlantOnly() {
        Company company = persistCompany();
        Plant plant = persistPlant(company, "PL-1", "Plant 1", OrganizationStatus.ACTIVE);
        Warehouse first = persistWarehouse(plant, "WH-RAW", "Raw Store", OrganizationStatus.ACTIVE);
        Warehouse second = persistWarehouse(plant, "WH-FG", "Finished Goods", OrganizationStatus.INACTIVE);
        persistWarehouse(persistPlant(company, "PL-2", "Plant 2", OrganizationStatus.ACTIVE),
                "WH-OTHER", "Other Plant Store", OrganizationStatus.ACTIVE);

        assertThat(warehouseRepository.search(plant.getPlantId(), null, null, page()).getContent())
                .extracting(Warehouse::getWarehouseId)
                .containsExactlyInAnyOrder(first.getWarehouseId(), second.getWarehouseId());
    }

    @Test
    void warehouseSearch_combinesSearchAndStatus() {
        Plant plant = persistPlant(persistCompany(), "PL-1", "Plant 1", OrganizationStatus.ACTIVE);
        Warehouse wanted = persistWarehouse(plant, "WH-RAW-1", "Raw Store", OrganizationStatus.ACTIVE);
        persistWarehouse(plant, "WH-RAW-2", "Raw Overflow", OrganizationStatus.INACTIVE);
        persistWarehouse(plant, "WH-FG", "Finished Goods", OrganizationStatus.ACTIVE);

        assertThat(warehouseRepository.search(plant.getPlantId(), OrganizationStatus.ACTIVE, "RAW", page())
                .getContent()).extracting(Warehouse::getWarehouseId).containsExactly(wanted.getWarehouseId());
    }

    @Test
    void existsByPlantAndStatus_seesOnlyActiveWarehousesOfThatPlant() {
        Company company = persistCompany();
        Plant plant = persistPlant(company, "PL-1", "Plant 1", OrganizationStatus.ACTIVE);
        persistWarehouse(plant, "WH-OLD", "Retired", OrganizationStatus.INACTIVE);
        Plant other = persistPlant(company, "PL-2", "Plant 2", OrganizationStatus.ACTIVE);
        persistWarehouse(other, "WH-LIVE", "Live", OrganizationStatus.ACTIVE);

        assertThat(warehouseRepository.existsByPlantPlantIdAndStatus(plant.getPlantId(), OrganizationStatus.ACTIVE))
                .isFalse();
        assertThat(warehouseRepository.existsByPlantPlantIdAndStatus(other.getPlantId(), OrganizationStatus.ACTIVE))
                .isTrue();
    }

    private static PageRequest page() {
        return PageRequest.of(0, 50);
    }

    private Company persistCompany() {
        String suffix = UUID.randomUUID().toString();
        Company company = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        stamp(company);
        return entityManager.persistFlushFind(company);
    }

    private Plant persistPlant(Company company, String code, String name, OrganizationStatus status) {
        Plant plant = Plant.builder().company(company).code(code).name(name).status(status).build();
        stamp(plant);
        return entityManager.persistFlushFind(plant);
    }

    private Warehouse persistWarehouse(Plant plant, String code, String name, OrganizationStatus status) {
        Warehouse warehouse = Warehouse.builder().plant(plant).code(code).name(name)
                .type(WarehouseType.RAW_MATERIAL).status(status).build();
        stamp(warehouse);
        return entityManager.persistFlushFind(warehouse);
    }

    private void stamp(BaseEntity entity) {
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
    }
}
