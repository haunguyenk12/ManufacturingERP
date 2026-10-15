package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.StockTransferLineRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.*;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stock transfer on a real database (G4-2, B150–B152). Only Postgres answers the three questions
 * here (rule R7): whether the whole document commits or rolls back as one, whether the source rows are
 * really locked so two transfers cannot both spend the same free stock, and whether the CHECK and
 * UNIQUE constraints of V72 accept what the service writes.
 *
 * <p>The {@code @DataJpaTest} wrapping transaction is off ({@code NOT_SUPPORTED}): what is being tested
 * is exactly what survives a commit, so each service call commits on its own as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({StockTransferService.class, InventoryMapper.class, PlantContextResolver.class, IdempotencySupport.class,
        TraceIdProvider.class, StockTransferIT.Config.class})
class StockTransferIT extends AbstractPostgresIntegrationTest {

    private static final int ROUNDS = 5;

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class Config {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    @Autowired StockTransferService service;
    @Autowired StockBalanceRepository balanceRepository;
    @Autowired StockMovementRepository movementRepository;
    @Autowired StockTransferRepository transferRepository;
    @Autowired InventoryLotRepository lotRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired JdbcTemplate jdbc;

    @MockBean UserLookupService userLookupService;

    @Test
    void transfer_commitsBothSidesOfEveryLine_andTheLotTravelsWithItsExpiry() {
        Fixture f = fixture();
        Instant expiry = Instant.parse("2031-01-31T00:00:00Z");
        InventoryLot lot = lotRepository.save(InventoryLot.builder().item(f.lotItem).lotCode("L-" + f.suffix)
                .status(LotStatus.AVAILABLE).receivedAt(Instant.parse("2026-10-01T00:00:00Z")).expiresAt(expiry).build());
        balance(f.item, f.from, null, "10");
        balance(f.lotItem, f.from, lot, "5");

        StockTransferResponse response = service.create(new StockTransferRequest(
                f.from.getWarehouseId(), f.to.getWarehouseId(), "Move to line side", List.of(
                        new StockTransferLineRequest(f.item.getItemId(), null, new BigDecimal("4")),
                        new StockTransferLineRequest(f.lotItem.getItemId(), lot.getLotId(), new BigDecimal("2")))),
                "TR-IT-" + f.suffix, null);

        assertThat(response.code()).matches("TR-[0-9A-F]{8}");
        // The create response and a re-read must agree to the microsecond (CLAUDE.md §0.51 #3).
        assertThat(transferRepository.findById(response.transferId()).orElseThrow().getPostedAt())
                .isEqualTo(response.postedAt());
        assertThat(quantity(f.item, f.from, null)).isEqualByComparingTo("6");
        assertThat(quantity(f.item, f.to, null)).isEqualByComparingTo("4");
        assertThat(quantity(f.lotItem, f.from, lot)).isEqualByComparingTo("3");
        assertThat(quantity(f.lotItem, f.to, lot)).isEqualByComparingTo("2");
        assertThat(lotRepository.findById(lot.getLotId()).orElseThrow().getExpiresAt()).isEqualTo(expiry);
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where reference_id = ?",
                Integer.class, response.transferId().toString())).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from stock_transfer_lines where transfer_id = ?",
                Integer.class, response.transferId())).isEqualTo(2);
    }

    /**
     * The second line's ledger insert collides with a row planted under the same child key, so the
     * failure happens <em>after</em> the first line has already moved stock in this transaction. Nothing
     * may survive: B150 is about the transaction boundary, not about validating before writing.
     */
    @Test
    void transfer_failingOnTheLastLineAfterTheFirstWasWritten_leavesNothingBehind() {
        Fixture f = fixture();
        Item second = itemRepository.save(Item.builder().company(f.company).code("TI2_" + f.suffix).name("Item 2")
                .type(ItemType.RAW_MATERIAL).unit("EA").status(ItemStatus.ACTIVE).build());
        balance(f.item, f.from, null, "10");
        balance(second, f.from, null, "10");
        String key = "TR-RB-" + f.suffix;
        movementRepository.save(StockMovement.builder().item(second).warehouse(f.from)
                .movementType(MovementType.TRANSFER_OUT).direction(MovementDirection.OUT)
                .quantity(BigDecimal.ONE).idempotencyKey(key + ":L2").createdAt(Instant.now()).build());

        assertThatThrownBy(() -> service.create(new StockTransferRequest(
                f.from.getWarehouseId(), f.to.getWarehouseId(), "Move", List.of(
                        new StockTransferLineRequest(f.item.getItemId(), null, new BigDecimal("3")),
                        new StockTransferLineRequest(second.getItemId(), null, new BigDecimal("3")))), key, null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(quantity(f.item, f.from, null)).isEqualByComparingTo("10");
        assertThat(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(
                f.item.getItemId(), f.to.getWarehouseId())).isEmpty();
        assertThat(transferRepository.findByIdempotencyKey(key)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where idempotency_key = ?",
                Integer.class, key + ":L1")).isZero();
    }

    /**
     * Two transfers race for the same free stock (10, each wants 6). B151: the source rows are locked
     * before free stock is read, so the loser waits, sees 4 and is refused — never a negative balance,
     * never an optimistic-lock 409 that hides which line was short.
     */
    @Test
    void concurrentTransfers_competingForTheLastFreeStock_oneWins_theOtherIsInsufficient() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Fixture f = fixture();
            balance(f.item, f.from, null, "10");

            List<Throwable> failures = race(f, new BigDecimal("6"));

            assertThat(failures).as("round %d", round).singleElement()
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));
            assertThat(quantity(f.item, f.from, null)).as("round %d", round).isEqualByComparingTo("4");
            assertThat(quantity(f.item, f.to, null)).as("round %d", round).isEqualByComparingTo("6");
        }
    }

    @Test
    void sameWarehouseOnBothSides_isRejectedByTheDatabaseToo() {
        Fixture f = fixture();
        assertThatThrownBy(() -> jdbc.update("""
                insert into stock_transfers (code, plant_id, from_warehouse_id, to_warehouse_id, reason,
                                             posted_at, idempotency_key)
                values (?, ?, ?, ?, 'x', now(), ?)
                """, "TR-SAME" + f.suffix, f.from.getPlant().getPlantId(), f.from.getWarehouseId(),
                f.from.getWarehouseId(), "K-" + f.suffix))
                .hasMessageContaining("chk_stock_transfers_distinct_warehouses");
    }

    private List<Throwable> race(Fixture f, BigDecimal quantity) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String key = "TR-RACE-" + f.suffix + "-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    service.create(new StockTransferRequest(f.from.getWarehouseId(), f.to.getWarehouseId(), "Race",
                            List.of(new StockTransferLineRequest(f.item.getItemId(), null, quantity))), key, null);
                    return null;
                }));
            }
            start.countDown();
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private BigDecimal quantity(Item item, Warehouse warehouse, InventoryLot lot) {
        Optional<StockBalance> row = lot == null
                ? balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(item.getItemId(), warehouse.getWarehouseId())
                : balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                        item.getItemId(), warehouse.getWarehouseId(), lot.getLotId());
        return row.orElseThrow().getQuantity();
    }

    private void balance(Item item, Warehouse warehouse, InventoryLot lot, String quantity) {
        balanceRepository.save(StockBalance.builder().item(item).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal(quantity)).build());
    }

    private record Fixture(String suffix, Company company, Warehouse from, Warehouse to, Item item, Item lotItem) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("TC_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("TP_" + suffix)
                .name("Plant " + suffix).status(OrganizationStatus.ACTIVE).build());
        Warehouse from = warehouseRepository.save(Warehouse.builder().plant(plant).code("TW1_" + suffix)
                .name("Source").type(WarehouseType.RAW_MATERIAL).status(OrganizationStatus.ACTIVE).build());
        Warehouse to = warehouseRepository.save(Warehouse.builder().plant(plant).code("TW2_" + suffix)
                .name("Destination").type(WarehouseType.RAW_MATERIAL).status(OrganizationStatus.ACTIVE).build());
        Item item = itemRepository.save(Item.builder().company(company).code("TI_" + suffix).name("Item " + suffix)
                .type(ItemType.RAW_MATERIAL).unit("EA").status(ItemStatus.ACTIVE).build());
        Item lotItem = itemRepository.save(Item.builder().company(company).code("TL_" + suffix).name("Lot item")
                .type(ItemType.RAW_MATERIAL).unit("KG").lotTracked(true).status(ItemStatus.ACTIVE).build());
        return new Fixture(suffix, company, from, to, item, lotItem);
    }
}
