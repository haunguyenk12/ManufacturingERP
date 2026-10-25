package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.SalesOrderCancelRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftLineRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftUpdateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.domain.UomStatus;
import com.erp.manufacturing.module.uom.service.UomLookupService;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.SalesOrderCoverageLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** G6a: customer-linked drafts, server-generated numbers, and the extended confirm/cancel. */
@ExtendWith(MockitoExtension.class)
class SalesOrderDraftServiceTest {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 10, 14);

    @Mock SalesOrderRepository salesOrderRepository;
    @Mock SalesOrderLineRepository salesOrderLineRepository;
    @Mock CustomerRepository customerRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemLookupService itemLookupService;
    @Mock UomLookupService uomLookupService;
    @Mock PlanningDemandService planningDemandService;
    @Mock SalesOrderStockReservationService stockReservationService;
    @Mock SalesOrderCoverageLookupService coverageLookupService;

    SalesOrderService service;
    Company company;
    Plant plant;
    Customer customer;
    Item finishedGood;

    @BeforeEach
    void setUp() {
        SalesOrderMapper mapper = new SalesOrderMapper();
        service = new SalesOrderService(salesOrderRepository, salesOrderLineRepository, customerRepository,
                organizationLookupService, itemLookupService, uomLookupService, planningDemandService,
                stockReservationService, coverageLookupService,
                new SalesOrderDetailAssembler(stockReservationService, coverageLookupService, mapper, org.mockito.Mockito.mock(SalesPriceVisibility.class)), mapper);
        company = Company.builder().companyId(UUID.randomUUID()).code("C").status(OrganizationStatus.ACTIVE).build();
        plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").status(OrganizationStatus.ACTIVE).build();
        customer = Customer.builder().customerId(UUID.randomUUID()).company(company).code("CUS-1").name("Khách A")
                .status(CustomerStatus.ACTIVE).build();
        finishedGood = item(ItemType.FINISHED_GOOD);
        lenient().when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        lenient().when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        lenient().when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId()))
                .thenReturn(Optional.of(customer));
        lenient().when(itemLookupService.getActiveItem(finishedGood.getItemId())).thenReturn(finishedGood);
        lenient().when(uomLookupService.findByCode(anyCollection())).thenReturn(Map.of());
    }

    @Test
    void createDraft_generatesTheNextFreeNumber_snapshotsTheCustomer_andNumbersLinesFromOne() {
        when(salesOrderRepository.nextOrderNumber()).thenReturn(1L, 2L);
        when(salesOrderRepository.existsByCompanyCompanyIdAndOrderNo(company.getCompanyId(), "SO-000001")).thenReturn(true);
        when(salesOrderRepository.existsByCompanyCompanyIdAndOrderNo(company.getCompanyId(), "SO-000002")).thenReturn(false);
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(i -> i.getArgument(0));

        SalesOrderResponse response = service.createDraft(request(
                line(finishedGood, "0.1", ORDER_DATE.plusDays(14)), line(finishedGood, "0.2", ORDER_DATE.plusDays(15))));

        assertThat(response.orderNo()).isEqualTo("SO-000002");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.customerId()).isEqualTo(customer.getCustomerId());
        assertThat(response.customerCode()).isEqualTo("CUS-1");
        assertThat(response.customerName()).isEqualTo("Khách A");
        assertThat(response.customerReference()).isEqualTo("PO-77");
        assertThat(response.lines()).extracting(l -> l.lineNo()).containsExactly(1, 2);
        assertThat(response.lines().get(0).uom()).isEqualTo("EA");
    }

    /** G10a (FE R02): a draft line price is optional; absent stays null, never 0. */
    @Test
    void createDraft_storesTheLinePricesAsSent_absentStaysNull() {
        when(salesOrderRepository.nextOrderNumber()).thenReturn(1L);
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(i -> i.getArgument(0));

        service.createDraft(request(
                new SalesOrderDraftLineRequest(finishedGood.getItemId(), BigDecimal.ONE, ORDER_DATE, new BigDecimal("0")),
                line(finishedGood, "1", ORDER_DATE)));

        org.mockito.ArgumentCaptor<SalesOrder> saved = org.mockito.ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getLines().get(0).getUnitPrice()).isEqualByComparingTo("0");
        assertThat(saved.getValue().getLines().get(1).getUnitPrice()).isNull();
        assertThat(saved.getValue().isDeliveryBaselineKnown()).isTrue();
    }

    /** G10a: replacing a draft with another price moves the pricing version; the same prices do not. */
    @Test
    void updateDraft_movesThePricingVersionOnlyWhenAPriceChanges() {
        SalesOrder order = draftOrder();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(order.getSalesOrderId())).thenReturn(Optional.of(order));
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(i -> i.getArgument(0));

        service.updateDraft(order.getSalesOrderId(), new SalesOrderDraftUpdateRequest(3L, customer.getCustomerId(), null,
                ORDER_DATE, null, List.of(line(finishedGood, "1", ORDER_DATE))));
        assertThat(order.getPricingRevision()).isZero();

        service.updateDraft(order.getSalesOrderId(), new SalesOrderDraftUpdateRequest(3L, customer.getCustomerId(), null,
                ORDER_DATE, null, List.of(new SalesOrderDraftLineRequest(
                        finishedGood.getItemId(), BigDecimal.ONE, ORDER_DATE, new BigDecimal("12.5")))));
        assertThat(order.getPricingRevision()).isEqualTo(1);
        assertThat(order.getLines().get(0).getUnitPrice()).isEqualByComparingTo("12.5");
    }

    /** G10a (D4): the legacy create may now link a master customer; its name replaces the free text. */
    @Test
    void legacyCreate_withACustomerId_linksTheMasterCustomer() {
        when(salesOrderRepository.existsByCompanyCompanyIdAndOrderNo(company.getCompanyId(), "SO-LEG")).thenReturn(false);
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(i -> i.getArgument(0));

        SalesOrderResponse response = service.create(new com.erp.manufacturing.module.sales.dto.SalesOrderCreateRequest(
                company.getCompanyId(), plant.getPlantId(), "SO-LEG", "typed name", ORDER_DATE, null,
                List.of(new com.erp.manufacturing.module.sales.dto.SalesOrderLineRequest(
                        finishedGood.getItemId(), BigDecimal.ONE, ORDER_DATE)),
                customer.getCustomerId()));

        assertThat(response.customerId()).isEqualTo(customer.getCustomerId());
        assertThat(response.customerName()).isEqualTo("Khách A");
        assertThat(response.customerCode()).isEqualTo("CUS-1");
    }

    @Test
    void createDraft_customerOfAnotherCompany_isAScopeMismatch() {
        customer.setCompany(Company.builder().companyId(UUID.randomUUID()).build());

        assertCode(() -> service.createDraft(request(line(finishedGood, "1", ORDER_DATE))),
                BusinessErrorCode.RESOURCE_SCOPE_MISMATCH);
        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDraft_inactiveCustomer_isRefused() {
        customer.deactivate();

        assertCode(() -> service.createDraft(request(line(finishedGood, "1", ORDER_DATE))),
                BusinessErrorCode.RESOURCE_INACTIVE);
        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    /** The due-date check names the field and runs before any item lookup (rule C9, FE A03). */
    @Test
    void createDraft_dueDateBeforeOrderDate_isAFieldErrorBeforeAnyItemLookup() {
        assertThatThrownBy(() -> service.createDraft(request(
                line(finishedGood, "1", ORDER_DATE), line(finishedGood, "1", ORDER_DATE.minusDays(1)))))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((MultiErrorException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[1].dueDate");
                });
        verifyNoInteractions(itemLookupService);
    }

    @Test
    void createDraft_rawMaterialLine_isNotSellable() {
        Item raw = item(ItemType.RAW_MATERIAL);
        when(itemLookupService.getActiveItem(raw.getItemId())).thenReturn(raw);

        assertCode(() -> service.createDraft(request(line(raw, "1", ORDER_DATE))), BusinessErrorCode.OPERATION_NOT_ALLOWED);
        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDraft_itemWhoseUnitIsInactiveInTheUomMaster_isRefused() {
        Uom ea = Uom.builder().code("EA").status(UomStatus.INACTIVE).build();
        when(uomLookupService.findByCode(anyCollection())).thenReturn(Map.of("EA", ea));

        assertCode(() -> service.createDraft(request(line(finishedGood, "1", ORDER_DATE))), BusinessErrorCode.RESOURCE_INACTIVE);
    }

    @Test
    void updateDraft_staleVersion_failsBeforeLookingAtTheCustomer() {
        SalesOrder order = draftOrder();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(order.getSalesOrderId())).thenReturn(Optional.of(order));

        assertCode(() -> service.updateDraft(order.getSalesOrderId(), new SalesOrderDraftUpdateRequest(
                        4L, customer.getCustomerId(), null, ORDER_DATE, null, List.of(line(finishedGood, "1", ORDER_DATE)))),
                BusinessErrorCode.CONCURRENT_MODIFICATION);
        verifyNoInteractions(customerRepository, itemLookupService);
    }

    /** FE R02: confirm re-checks the master data of an order linked to a customer. */
    @Test
    void confirm_customerDeactivatedSinceTheDraft_isRefusedAndCreatesNoDemand() {
        SalesOrder order = draftOrder();
        order.setCustomerId(customer.getCustomerId());
        customer.deactivate();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(order.getSalesOrderId())).thenReturn(Optional.of(order));

        assertCode(() -> service.confirm(order.getSalesOrderId(), null), BusinessErrorCode.RESOURCE_INACTIVE);
        verifyNoInteractions(planningDemandService);
    }

    @Test
    void confirm_versionMismatch_isAConcurrentModification() {
        SalesOrder order = draftOrder();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(order.getSalesOrderId())).thenReturn(Optional.of(order));

        assertCode(() -> service.confirm(order.getSalesOrderId(), 9L), BusinessErrorCode.CONCURRENT_MODIFICATION);
        verifyNoInteractions(planningDemandService);
    }

    @Test
    void cancel_confirmedOrderWithABody_releasesItsHoldsAndStoresTheReason() {
        SalesOrder order = draftOrder();
        order.confirm();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(order.getSalesOrderId())).thenReturn(Optional.of(order));
        when(salesOrderRepository.saveAndFlush(order)).thenReturn(order);

        SalesOrderResponse response = service.cancel(order.getSalesOrderId(),
                new SalesOrderCancelRequest(3L, "  Customer withdrew  "));

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancelReason()).isEqualTo("Customer withdrew");
        verify(stockReservationService).cancelActiveForSalesOrder(order.getSalesOrderId());
    }

    @Test
    void cancel_draftOrder_hasNoHoldsToRelease() {
        SalesOrder order = draftOrder();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(order.getSalesOrderId())).thenReturn(Optional.of(order));
        when(salesOrderRepository.saveAndFlush(order)).thenReturn(order);

        service.cancel(order.getSalesOrderId(), null);

        verify(stockReservationService, never()).cancelActiveForSalesOrder(any());
    }

    private SalesOrder draftOrder() {
        SalesOrder order = SalesOrder.builder().salesOrderId(UUID.randomUUID()).company(company).plant(plant)
                .orderNo("SO-000009").customerName("Khách A").orderDate(ORDER_DATE)
                .status(SalesOrderStatus.DRAFT).build();
        order.setVersion(3L);
        order.getLines().add(SalesOrderLine.builder().salesOrderLineId(UUID.randomUUID()).salesOrder(order)
                .item(finishedGood).lineNo(1).orderedQuantity(BigDecimal.TEN).fulfilledQuantity(BigDecimal.ZERO)
                .dueDate(ORDER_DATE.plusDays(5)).build());
        return order;
    }

    private SalesOrderDraftCreateRequest request(SalesOrderDraftLineRequest... lines) {
        return new SalesOrderDraftCreateRequest(company.getCompanyId(), plant.getPlantId(), customer.getCustomerId(),
                " PO-77 ", ORDER_DATE, null, List.of(lines));
    }

    private static SalesOrderDraftLineRequest line(Item item, String quantity, LocalDate dueDate) {
        return new SalesOrderDraftLineRequest(item.getItemId(), new BigDecimal(quantity), dueDate);
    }

    private Item item(ItemType type) {
        return Item.builder().itemId(UUID.randomUUID()).company(company).code(type.name()).name(type.name())
                .type(type).unit("EA").status(ItemStatus.ACTIVE).build();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
