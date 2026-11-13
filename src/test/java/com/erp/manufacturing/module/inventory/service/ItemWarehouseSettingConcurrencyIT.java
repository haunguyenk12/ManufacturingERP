package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSetting;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSettingStatus;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Two concurrent upserts claiming the same default role for one item and plant (G2-3, FE R8).
 *
 * <p>Only a real database answers this (rule {@code R7}): the guarantee comes from the row lock
 * {@code ItemRepository.lockByItemId} takes before the service reads the other warehouses' flags, and
 * from trigger {@code trg_item_warehouse_default_role} (V66). Without the lock both requests read
 * "nobody holds the role" and both claim it.
 *
 * <p>Each upsert must commit for the other thread to see it, so the {@code @DataJpaTest} wrapping
 * transaction is switched off ({@code NOT_SUPPORTED}); every repository call and every service call
 * commits on its own, exactly as in production. Rows are not rolled back — every round uses fresh
 * codes, so rounds and other test classes never collide.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ItemWarehouseSettingService.class, InventoryMapper.class, PlantContextResolver.class, OrganizationLookupService.class,
        ItemWarehouseSettingConcurrencyIT.AuditingConfig.class})
class ItemWarehouseSettingConcurrencyIT extends AbstractPostgresIntegrationTest {

    private static final int ROUNDS = 5;

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }
    }

    @Autowired ItemWarehouseSettingService service;
    @Autowired ItemWarehouseSettingRepository settingRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;

    @MockBean ItemLookupService itemLookupService;

    /**
     * Both requests confirmed (replaceDefaults=true): both succeed — the second simply moves the role
     * the first just took — and the plant ends with exactly one supply default.
     */
    @Test
    void concurrentConfirmedClaims_bothSucceed_andExactlyOneDefaultRemains() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Fixture f = fixture();

            List<Throwable> failures = race(f, Boolean.TRUE);

            assertThat(failures).as("round %d", round).isEmpty();
            assertThat(supplyDefaults(f)).as("round %d", round).hasSize(1);
        }
    }

    /**
     * Both requests unconfirmed (replaceDefaults=false): serialized by the lock, the first wins and the
     * second sees the first's role and is refused with DEFAULT_CONFLICT — never two defaults, never a 500.
     */
    @Test
    void concurrentUnconfirmedClaims_oneWins_theOtherGetsDefaultConflict() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Fixture f = fixture();

            List<Throwable> failures = race(f, Boolean.FALSE);

            assertThat(failures).as("round %d", round).singleElement()
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(BusinessErrorCode.DEFAULT_CONFLICT));
            assertThat(supplyDefaults(f)).as("round %d", round).hasSize(1);
        }
    }

    /** Starts one upsert per warehouse at the same instant and returns whatever they threw. */
    private List<Throwable> race(Fixture f, Boolean replaceDefaults) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Warehouse warehouse : List.of(f.first, f.second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    service.upsert(new ItemWarehouseSettingRequest(f.item.getItemId(), warehouse.getWarehouseId(),
                            BigDecimal.ZERO, BigDecimal.ZERO, 0, true, false, replaceDefaults), null);
                    return null;
                }));
            }
            start.countDown();
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<ItemWarehouseSetting> supplyDefaults(Fixture f) {
        return settingRepository.findByItemItemIdAndWarehousePlantPlantIdAndStatus(
                        f.item.getItemId(), f.first.getPlant().getPlantId(), ItemWarehouseSettingStatus.ACTIVE)
                .stream()
                .filter(ItemWarehouseSetting::isDefaultSupply)
                .toList();
    }

    private record Fixture(Item item, Warehouse first, Warehouse second) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("CC_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("CP_" + suffix)
                .name("Plant " + suffix).status(OrganizationStatus.ACTIVE).build());
        Warehouse first = warehouseRepository.save(Warehouse.builder().plant(plant).code("CW1_" + suffix)
                .name("Warehouse 1").type(WarehouseType.RAW_MATERIAL).status(OrganizationStatus.ACTIVE).build());
        Warehouse second = warehouseRepository.save(Warehouse.builder().plant(plant).code("CW2_" + suffix)
                .name("Warehouse 2").type(WarehouseType.RAW_MATERIAL).status(OrganizationStatus.ACTIVE).build());
        Item item = itemRepository.save(Item.builder().company(company).code("CI_" + suffix).name("Item " + suffix)
                .type(ItemType.RAW_MATERIAL).unit("EA").status(ItemStatus.ACTIVE).build());
        when(itemLookupService.getActiveItem(any())).thenReturn(item);
        return new Fixture(item, first, second);
    }
}
