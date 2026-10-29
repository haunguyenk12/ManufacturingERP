package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.GlobalExceptionHandler;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrder;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderLine;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import com.erp.manufacturing.module.purchasing.domain.Supplier;
import com.erp.manufacturing.module.purchasing.domain.SupplierStatus;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptDraftCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptDraftLineRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptResponse;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptVersionRequest;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.SupplierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CF-9: two goods receipts of the same lot-tracked item, on two different purchase orders, posted at
 * the same instant with lot codes {@code LOT-X} and {@code lot-x}.
 *
 * <p>The two posts lock two different purchase orders, so nothing in the application serialises
 * them: both can pass the case-insensitive "lot code already exists" check before either commits.
 * Only V95's {@code uk_inventory_lots_item_code_ci} stops the second lot; V8's index compares case
 * sensitively and lets both in. Only a real database answers this (rule R7).
 *
 * <p>The loser is answered either by the service (if its check ran after the winner committed) or by
 * the index. In the second case the {@link DataIntegrityViolationException} is run through
 * {@link GlobalExceptionHandler}, so the assertion is on the code the client really receives.
 *
 * <p>The {@code @DataJpaTest} wrapping transaction is off: each post commits on its own, as in
 * production. Every round uses fresh rows.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({GoodsReceiptDraftService.class, GoodsReceiptPoster.class, PurchasingIdempotency.class, PurchasingMapper.class,
        IdempotencySupport.class, TraceIdProvider.class, InventoryMovementService.class, ItemLookupService.class,
        OrganizationLookupService.class, GoodsReceiptLotCodeConcurrencyIT.Config.class})
class GoodsReceiptLotCodeConcurrencyIT extends AbstractPostgresIntegrationTest {

    private static final String ZONE = "Asia/Ho_Chi_Minh";
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

    @Autowired GoodsReceiptDraftService service;
    @Autowired PurchaseOrderRepository orderRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired JdbcTemplate jdbc;

    private record Fixture(Company company, Plant plant, Warehouse warehouse, Supplier supplier) {}

    @Test
    void concurrentPostsOfTwoOrders_withCaseOnlyDifferentLotCodes_exactlyOneLotIsCreated() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Fixture f = fixture();
            Item item = lotTrackedItem(f);
            PurchaseOrder firstOrder = order(f, item);
            PurchaseOrder secondOrder = order(f, item);
            GoodsReceiptResponse upper = draft(firstOrder, "LOT-X");
            GoodsReceiptResponse lower = draft(secondOrder, "lot-x");

            List<Object> outcomes = race(List.of(upper, lower));

            final int r = round;
            assertThat(outcomes).as("round %d", round).filteredOn(GoodsReceiptResponse.class::isInstance).hasSize(1);
            Throwable failure = (Throwable) outcomes.stream().filter(Throwable.class::isInstance).findFirst()
                    .orElseThrow(() -> new AssertionError("round " + r + ": both posts succeeded, two lots LOT-X / lot-x"));
            assertThat(errorCodeSeenByTheClient(failure)).as("round %d", round)
                    .isEqualTo(BusinessErrorCode.LOT_CODE_ALREADY_EXISTS.code());

            assertThat(jdbc.queryForObject("select count(*) from inventory_lots where item_id = ?",
                    Integer.class, item.getItemId())).as("round %d", round).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from stock_movements where item_id = ?",
                    Integer.class, item.getItemId())).as("round %d", round).isEqualTo(1);

            GoodsReceiptResponse winner = (GoodsReceiptResponse) outcomes.stream()
                    .filter(GoodsReceiptResponse.class::isInstance).findFirst().orElseThrow();
            boolean upperWon = winner.goodsReceiptId().equals(upper.goodsReceiptId());
            GoodsReceiptResponse loser = upperWon ? lower : upper;
            PurchaseOrder loserOrder = upperWon ? secondOrder : firstOrder;
            PurchaseOrder winnerOrder = upperWon ? firstOrder : secondOrder;
            assertThat(jdbc.queryForObject("select status from goods_receipts where goods_receipt_id = ?",
                    String.class, loser.goodsReceiptId())).as("round %d", round).isEqualTo("DRAFT");
            assertThat(receivedQuantity(loserOrder)).as("round %d", round).isEqualByComparingTo("0");
            assertThat(receivedQuantity(winnerOrder)).as("round %d", round).isEqualByComparingTo("3");
            assertThat(jdbc.queryForObject("select lot_code from inventory_lots where item_id = ?",
                    String.class, item.getItemId())).as("round %d", round).isEqualTo(upperWon ? "LOT-X" : "lot-x");
        }
    }

    /** The index is per item: the same code, in another case, on another item is still a new lot. */
    @Test
    void sameLotCodeOnAnotherItem_isStillAccepted() {
        Fixture f = fixture();
        Item first = lotTrackedItem(f);
        Item second = lotTrackedItem(f);
        GoodsReceiptResponse a = draft(order(f, first), "LOT-X");
        GoodsReceiptResponse b = draft(order(f, second), "lot-x");

        service.post(a.goodsReceiptId(), new GoodsReceiptVersionRequest(a.version()), null);
        GoodsReceiptResponse posted = service.post(b.goodsReceiptId(), new GoodsReceiptVersionRequest(b.version()), null);

        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(jdbc.queryForObject("select lot_code from inventory_lots where item_id = ?",
                String.class, second.getItemId())).isEqualTo("lot-x");
        assertThat(jdbc.queryForObject("select lot_code from inventory_lots where item_id = ?",
                String.class, first.getItemId())).isEqualTo("LOT-X");
    }

    /** The {@code $.code} the client gets for this failure, whichever layer raised it. */
    private static String errorCodeSeenByTheClient(Throwable failure) {
        if (failure instanceof AppException app) {
            return app.getErrorCode().code();
        }
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleDataIntegrity((DataIntegrityViolationException) failure, new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        return response.getBody().code();
    }

    private List<Object> race(List<GoodsReceiptResponse> drafts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(drafts.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (GoodsReceiptResponse draft : drafts) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        return service.post(draft.goodsReceiptId(), new GoodsReceiptVersionRequest(draft.version()), null);
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdown();
        }
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("LC_" + suffix).name("Co")
                .status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("P_" + suffix).name("Plant").timezone(ZONE)
                .status(OrganizationStatus.ACTIVE).build());
        Warehouse warehouse = warehouseRepository.save(Warehouse.builder().plant(plant).code("W_" + suffix).name("Wh")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build());
        Supplier supplier = supplierRepository.save(Supplier.builder().company(company).code("S_" + suffix).name("Acme")
                .status(SupplierStatus.ACTIVE).build());
        return new Fixture(company, plant, warehouse, supplier);
    }

    private Item lotTrackedItem(Fixture f) {
        return itemRepository.save(Item.builder().company(f.company()).code("I_" + UUID.randomUUID().toString().substring(0, 8))
                .name("Raw").type(ItemType.RAW_MATERIAL).unit("KG").lotTracked(true).status(ItemStatus.ACTIVE).build());
    }

    private PurchaseOrder order(Fixture f, Item item) {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));
        PurchaseOrder order = PurchaseOrder.builder().company(f.company()).plant(f.plant()).warehouse(f.warehouse())
                .supplier(f.supplier()).purchaseOrderNo("PO-" + UUID.randomUUID().toString().substring(0, 8))
                .status(PurchaseOrderStatus.CONFIRMED).orderDate(today.minusDays(2)).expectedDate(today).build();
        order.getLines().add(PurchaseOrderLine.builder().purchaseOrder(order).item(item)
                .orderedQuantity(new BigDecimal("10")).receivedQuantity(BigDecimal.ZERO)
                .unitPrice(new BigDecimal("10")).currencyCode("VND").expectedDate(today).build());
        return orderRepository.saveAndFlush(order);
    }

    private GoodsReceiptResponse draft(PurchaseOrder order, String lotCode) {
        UUID lineId = jdbc.queryForObject("select purchase_order_line_id from purchase_order_lines where purchase_order_id = ?",
                UUID.class, order.getPurchaseOrderId());
        return service.createDraft(new GoodsReceiptDraftCreateRequest(order.getPurchaseOrderId(),
                LocalDate.now(ZoneId.of(ZONE)), null, null,
                List.of(new GoodsReceiptDraftLineRequest(null, lineId, new BigDecimal("3"), lotCode, null))), null);
    }

    private BigDecimal receivedQuantity(PurchaseOrder order) {
        return jdbc.queryForObject("select received_quantity from purchase_order_lines where purchase_order_id = ?",
                BigDecimal.class, order.getPurchaseOrderId());
    }
}
