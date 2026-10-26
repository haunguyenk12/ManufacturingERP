package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
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
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.dto.SalesOrderCancelRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftLineRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationResult;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import com.erp.manufacturing.module.uom.service.UomLookupService;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.SalesOrderCoverageLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * Stock held for a sales order (G6a, NEXT_PHASE_PLAN.md §13.2 D4) on a real database, with every
 * service call committing on its own ({@code NOT_SUPPORTED}) as in production. What only Postgres can
 * answer (rule R7): the FEFO/eligibility query, the shared {@code reserved_quantity} on
 * {@code stock_balances}, the {@code @Version} bump that makes a blind retry fail, the order-number
 * sequence, and the optional-parameter casts of the extended list query.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SalesOrderService.class, SalesOrderReservationService.class, SalesOrderDetailAssembler.class,
        SalesOrderMapper.class, SalesOrderStockReservationService.class, SalesOrderCoverageLookupService.class,
        SalesOrderReservationIT.Config.class})
class SalesOrderReservationIT extends AbstractPostgresIntegrationTest {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 10, 14);

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class Config {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }
    }

    @Autowired SalesOrderService salesOrderService;
    @Autowired SalesOrderReservationService reservationService;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired InventoryLotRepository lotRepository;
    @Autowired StockBalanceRepository balanceRepository;
    @Autowired JdbcTemplate jdbc;

    @MockBean com.erp.manufacturing.module.sales.service.SalesPriceVisibility salesPriceVisibility;
    @MockBean com.erp.manufacturing.module.delivery.service.DeliveryLookupService deliveryLookupService;

    @MockBean OrganizationLookupService organizationLookupService;
    @MockBean ItemLookupService itemLookupService;
    @MockBean PlanningDemandService planningDemandService;
    @MockBean UomLookupService uomLookupService;

    private Company company;
    private Plant plant;
    private Warehouse warehouse;
    private Item item;
    private Customer customer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = companyRepository.save(Company.builder().code("SC_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build());
        plant = plantRepository.save(Plant.builder().company(company).code("SP_" + suffix)
                .name("Plant " + suffix).status(OrganizationStatus.ACTIVE).build());
        warehouse = warehouseRepository.save(Warehouse.builder().plant(plant).code("SW_" + suffix)
                .name("FG").type(WarehouseType.FINISHED_GOODS).status(OrganizationStatus.ACTIVE).build());
        item = itemRepository.save(Item.builder().company(company).code("SF_" + suffix).name("Bike " + suffix)
                .type(ItemType.FINISHED_GOOD).unit("EA").lotTracked(true).status(ItemStatus.ACTIVE).build());
        customer = customerRepository.save(Customer.builder().company(company).code("CUS_" + suffix)
                .name("Customer " + suffix).status(CustomerStatus.ACTIVE).build());

        when(organizationLookupService.getActiveCompany(any())).thenAnswer(i -> companyRepository.findById(i.getArgument(0)).orElseThrow());
        when(organizationLookupService.getActivePlant(any())).thenAnswer(i -> plantRepository.findById(i.getArgument(0)).orElseThrow());
        when(organizationLookupService.getActiveWarehouseInPlant(any(), any()))
                .thenAnswer(i -> warehouseRepository.findById(i.getArgument(0)).orElseThrow());
        when(itemLookupService.getActiveItem(any())).thenAnswer(i -> itemRepository.findById(i.getArgument(0))
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Item", i.getArgument(0))));
        when(uomLookupService.findByCode(anyCollection())).thenReturn(Map.of());
    }

    @Test
    void reserveWithoutALot_spreadsFefoOverEligibleLots_skippingHeldAndExpiredOnes_andAStaleRetryHoldsNothingMore() {
        Instant now = Instant.now();
        StockBalance early = lotBalance("EARLY", LotStatus.AVAILABLE, now.plus(10, ChronoUnit.DAYS), "4");
        StockBalance late = lotBalance("LATE", LotStatus.AVAILABLE, now.plus(90, ChronoUnit.DAYS), "20");
        StockBalance held = lotBalance("HELD", LotStatus.HOLD, now.plus(5, ChronoUnit.DAYS), "50");
        StockBalance expired = lotBalance("EXPIRED", LotStatus.AVAILABLE, now.minus(1, ChronoUnit.DAYS), "50");
        SalesOrderResponse order = confirmedOrder("10");
        UUID lineId = order.lines().get(0).salesOrderLineId();

        SalesOrderReservationResult result = reservationService.reserve(order.salesOrderId(),
                new SalesOrderReservationCreateRequest(order.version(), lineId, warehouse.getWarehouseId(), null,
                        new BigDecimal("7")));

        assertThat(result.reservations()).extracting(r -> r.lotCode().substring(0, r.lotCode().indexOf('-')))
                .containsExactly("EARLY", "LATE");
        assertThat(result.reservations()).extracting(r -> r.quantity().stripTrailingZeros().toPlainString())
                .containsExactly("4", "3");
        assertThat(reserved(early)).isEqualByComparingTo("4");
        assertThat(reserved(late)).isEqualByComparingTo("3");
        assertThat(reserved(held)).isZero();
        assertThat(reserved(expired)).isZero();
        var line = result.salesOrder().lines().get(0);
        assertThat(line.heldStockQuantity()).isEqualByComparingTo("7");
        assertThat(line.uncoveredQuantity()).isEqualByComparingTo("3");
        assertThat(result.salesOrder().version()).isEqualTo(order.version() + 1);

        // A blind retry with the version the client already used must not hold the stock twice.
        assertThatThrownBy(() -> reservationService.reserve(order.salesOrderId(),
                new SalesOrderReservationCreateRequest(order.version(), lineId, warehouse.getWarehouseId(), null,
                        new BigDecimal("3"))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        assertThat(reserved(early).add(reserved(late))).isEqualByComparingTo("7");
    }

    @Test
    void reserveMoreThanTheLineStillNeeds_isRefusedAndHoldsNothing() {
        StockBalance balance = lotBalance("ONLY", LotStatus.AVAILABLE, null, "50");
        SalesOrderResponse order = confirmedOrder("10");

        assertThatThrownBy(() -> reservationService.reserve(order.salesOrderId(),
                new SalesOrderReservationCreateRequest(order.version(), order.lines().get(0).salesOrderLineId(),
                        warehouse.getWarehouseId(), null, new BigDecimal("10.000001"))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED));
        assertThat(reserved(balance)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM material_reservations WHERE sales_order_id = ?",
                Integer.class, order.salesOrderId())).isZero();
    }

    @Test
    void cancellingTheOrder_returnsEveryHoldToFreeStock_andKeepsTheReason() {
        StockBalance balance = lotBalance("ONLY", LotStatus.AVAILABLE, null, "50");
        SalesOrderResponse order = confirmedOrder("10");
        SalesOrderReservationResult held = reservationService.reserve(order.salesOrderId(),
                new SalesOrderReservationCreateRequest(order.version(), order.lines().get(0).salesOrderLineId(),
                        warehouse.getWarehouseId(), null, new BigDecimal("6")));
        assertThat(reserved(balance)).isEqualByComparingTo("6");

        SalesOrderResponse cancelled = salesOrderService.cancel(order.salesOrderId(),
                new SalesOrderCancelRequest(held.salesOrder().version(), "Customer withdrew"));

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelReason()).isEqualTo("Customer withdrew");
        assertThat(reserved(balance)).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM material_reservations WHERE sales_order_id = ?",
                String.class, order.salesOrderId())).containsOnly("CANCELLED");
    }

    @Test
    void draftsGetDistinctServerNumbers_andTheListFiltersWorkWithAndWithoutEveryOptionalParameter() {
        SalesOrderResponse first = draft("5", ORDER_DATE.plusDays(3));
        SalesOrderResponse second = draft("5", ORDER_DATE.plusDays(20));

        assertThat(first.orderNo()).startsWith("SO-").isNotEqualTo(second.orderNo());
        assertThat(first.customerId()).isEqualTo(customer.getCustomerId());
        assertThat(first.customerCode()).isEqualTo(customer.getCode());

        var all = salesOrderService.list(company.getCompanyId(), plant.getPlantId(), null, null,
                null, null, null, null, PageRequest.of(0, 20));
        var dueSoon = salesOrderService.list(company.getCompanyId(), plant.getPlantId(), null, customer.getCode(),
                ORDER_DATE, ORDER_DATE, ORDER_DATE, ORDER_DATE.plusDays(10), PageRequest.of(0, 20));

        assertThat(all.content()).extracting(SalesOrderResponse::orderNo)
                .containsExactlyInAnyOrder(first.orderNo(), second.orderNo());
        assertThat(dueSoon.content()).singleElement().satisfies(row -> {
            assertThat(row.orderNo()).isEqualTo(first.orderNo());
            assertThat(row.lineCount()).isEqualTo(1);
            assertThat(row.earliestDueDate()).isEqualTo(ORDER_DATE.plusDays(3));
        });
    }

    private SalesOrderResponse draft(String quantity, LocalDate dueDate) {
        return salesOrderService.createDraft(new SalesOrderDraftCreateRequest(
                company.getCompanyId(), plant.getPlantId(), customer.getCustomerId(), "PO-1", ORDER_DATE, null,
                List.of(new SalesOrderDraftLineRequest(item.getItemId(), new BigDecimal(quantity), dueDate))));
    }

    private SalesOrderResponse confirmedOrder(String quantity) {
        SalesOrderResponse draft = draft(quantity, ORDER_DATE.plusDays(14));
        return salesOrderService.confirm(draft.salesOrderId(), draft.version());
    }

    private StockBalance lotBalance(String prefix, LotStatus status, Instant expiresAt, String quantity) {
        InventoryLot lot = lotRepository.save(InventoryLot.builder().item(item)
                .lotCode(prefix + "-" + UUID.randomUUID().toString().substring(0, 6))
                .status(status).receivedAt(Instant.now()).expiresAt(expiresAt).build());
        return balanceRepository.save(StockBalance.builder().item(item).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal(quantity)).build());
    }

    private BigDecimal reserved(StockBalance balance) {
        return balanceRepository.findById(balance.getBalanceId()).orElseThrow().getReservedQuantity();
    }
}
