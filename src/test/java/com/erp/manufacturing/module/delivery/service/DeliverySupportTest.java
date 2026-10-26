package com.erp.manufacturing.module.delivery.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.delivery.repository.DeliveryLineRepository;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.StockPositionRef;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** G10b (FE R05–R08): the quota, stock and source rules a delivery is checked against on save and at post. */
@ExtendWith(MockitoExtension.class)
class DeliverySupportTest {

    @Mock DeliveryLineRepository deliveryLineRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemLookupService itemLookupService;
    @Mock InventoryMovementService inventoryMovementService;
    @Mock SalesOrderCommercialLookupService salesOrderLookupService;

    DeliverySupport support;
    SalesOrder order;
    SalesOrderLine line;
    Item item;
    Warehouse warehouse;
    InventoryLot lot;
    Customer customer;

    @BeforeEach
    void setUp() {
        support = new DeliverySupport(deliveryLineRepository, organizationLookupService, itemLookupService,
                inventoryMovementService, salesOrderLookupService);
        Company company = Company.builder().companyId(UUID.randomUUID()).code("C").build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P")
                .timezone("Asia/Ho_Chi_Minh").build();
        warehouse = Warehouse.builder().warehouseId(UUID.randomUUID()).plant(plant).code("FG").build();
        item = Item.builder().itemId(UUID.randomUUID()).company(company).code("FG-1").lotTracked(true).build();
        lot = InventoryLot.builder().lotId(UUID.randomUUID()).item(item).lotCode("L1").status(LotStatus.AVAILABLE).build();
        customer = Customer.builder().customerId(UUID.randomUUID()).company(company).code("CUS").name("A")
                .status(CustomerStatus.ACTIVE).build();
        order = SalesOrder.builder().salesOrderId(UUID.randomUUID()).company(company).plant(plant).orderNo("SO-1")
                .customerName("A").customerId(customer.getCustomerId()).orderDate(LocalDate.of(2026, 10, 1))
                .status(SalesOrderStatus.CONFIRMED).build();
        line = SalesOrderLine.builder().salesOrderLineId(UUID.randomUUID()).salesOrder(order).item(item).lineNo(1)
                .orderedQuantity(BigDecimal.TEN).dueDate(LocalDate.of(2026, 10, 9)).build();
        order.getLines().add(line);
        lenient().when(salesOrderLookupService.findCustomer(customer.getCustomerId())).thenReturn(Optional.of(customer));
    }

    @Test
    void blockedReason_namesEachGapAndIsNullForADeliverableOrder() {
        assertThat(support.blockedReason(order)).isNull();
        order.setStatus(SalesOrderStatus.FULFILLED);
        assertThat(support.blockedReason(order)).as("QC-complete orders still have to ship").isNull();
        order.setDeliveryBaselineKnown(false);
        assertThat(support.blockedReason(order)).isEqualTo("BASELINE_UNKNOWN");
        order.setDeliveryBaselineKnown(true);
        order.setCustomerId(null);
        assertThat(support.blockedReason(order)).isEqualTo("CUSTOMER_MISSING");
        order.setCustomerId(customer.getCustomerId());
        customer.deactivate();
        assertThat(support.blockedReason(order)).isEqualTo("CUSTOMER_INACTIVE");
        order.setStatus(SalesOrderStatus.DRAFT);
        assertThat(support.blockedReason(order)).isEqualTo("ORDER_STATUS");
    }

    @Test
    void ensureDeliverable_aLegacyOrderIsSourceUnknown_aDraftIsStateConflict_anInactiveCustomerIsInactive() {
        order.setDeliveryBaselineKnown(false);
        assertCode(() -> support.ensureDeliverable(order), BusinessErrorCode.SOURCE_UNKNOWN);
        order.setDeliveryBaselineKnown(true);
        order.setStatus(SalesOrderStatus.CANCELLED);
        assertCode(() -> support.ensureDeliverable(order), BusinessErrorCode.STATE_CONFLICT);
        order.setStatus(SalesOrderStatus.CONFIRMED);
        customer.deactivate();
        assertCode(() -> support.ensureDeliverable(order), BusinessErrorCode.RESOURCE_INACTIVE);
    }

    /** FE §3.5: ordered 10, delivered 3, another draft 5 ⇒ this draft can claim 2, not 7. */
    @Test
    void ensureQuota_isOrderedMinusDeliveredMinusOtherDrafts_theLineThatCrossesCarriesTheError() {
        when(deliveryLineRepository.sumPostedBySalesOrderLineIds(anyCollection())).thenReturn(List.of(sum(line, "3")));
        UUID editing = UUID.randomUUID();
        when(deliveryLineRepository.sumDraftBySalesOrderLineIds(anyCollection(), eq(editing)))
                .thenReturn(List.of(sum(line, "5")));

        support.ensureQuota(List.of(resolved(0, "1"), resolved(1, "1")), editing);

        assertThatThrownBy(() -> support.ensureQuota(List.of(resolved(0, "1"), resolved(1, "1.000001")), editing))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((MultiErrorException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[1].quantity");
                });
    }

    /** FE A09: eligible 8, another order holds 3, this line holds 2 ⇒ free 3, usable 5 — never 8. */
    @Test
    void ensureStock_ownHoldsPlusFreeStock_neverAnotherOrdersHold() {
        StockBalance balance = StockBalance.builder().item(item).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal("8")).reservedQuantity(new BigDecimal("5"))
                .qualityHoldQuantity(BigDecimal.ZERO).build();
        when(inventoryMovementService.findPosition(any())).thenReturn(Optional.of(balance));
        List<MaterialReservation> holds = List.of(hold(line.getSalesOrderLineId(), "2"));

        support.ensureStock(order, List.of(resolved(0, "5")), holds);

        assertCode(() -> support.ensureStock(order, List.of(resolved(0, "5.000001")), holds),
                BusinessErrorCode.INSUFFICIENT_STOCK);
        assertCode(() -> support.ensureStock(order, List.of(resolved(0, "3.5")), List.of()),
                BusinessErrorCode.INSUFFICIENT_STOCK);
    }

    /** FE A10: two lines on one pool of 5 asking 3 + 3 fail together, on the last line. */
    @Test
    void ensureStock_linesOnTheSamePositionShareItsFreeQuantity() {
        StockBalance balance = StockBalance.builder().item(item).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal("5")).reservedQuantity(BigDecimal.ZERO)
                .qualityHoldQuantity(BigDecimal.ZERO).build();
        when(inventoryMovementService.findPosition(any())).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> support.ensureStock(order, List.of(resolved(0, "3"), resolved(1, "3")), List.of()))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[1].quantity"));
    }

    @Test
    void freeQuantity_isZeroForAHeldOrExpiredLot() {
        StockBalance balance = StockBalance.builder().item(item).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal("5")).reservedQuantity(BigDecimal.ONE).qualityHoldQuantity(BigDecimal.ZERO).build();
        Instant now = Instant.now();
        assertThat(DeliverySupport.freeQuantity(balance, now)).isEqualByComparingTo("4");
        lot.setExpiresAt(now.minusSeconds(1));
        assertThat(DeliverySupport.freeQuantity(balance, now)).isEqualByComparingTo("0");
        lot.setExpiresAt(null);
        lot.setStatus(LotStatus.HOLD);
        assertThat(DeliverySupport.freeQuantity(balance, now)).isEqualByComparingTo("0");
    }

    @Test
    void sourceKey_roundTripsWithAndWithoutALot_andAForgedKeyIsAFieldError() {
        UUID w = UUID.randomUUID();
        UUID l = UUID.randomUUID();
        assertThat(DeliverySupport.decodeSourceKey(DeliverySupport.sourceKey(w, l), item.getItemId(), "f"))
                .isEqualTo(new StockPositionRef(item.getItemId(), w, l));
        assertThat(DeliverySupport.decodeSourceKey(DeliverySupport.sourceKey(w, null), item.getItemId(), "f"))
                .isEqualTo(new StockPositionRef(item.getItemId(), w, null));
        assertThatThrownBy(() -> DeliverySupport.decodeSourceKey("not-a-key", item.getItemId(), "lines[0].sourceKey"))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((MultiErrorException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[0].sourceKey");
                });
    }

    @Test
    void ensureDate_notAfterThePlantsTodayNorBeforeTheOrderDate() {
        LocalDate today = DeliverySupport.plantToday(order);
        support.ensureDate(order, today);
        assertCode(() -> support.ensureDate(order, today.plusDays(1)), ValidationErrorCode.INVALID_INPUT);
        assertCode(() -> support.ensureDate(order, order.getOrderDate().minusDays(1)), ValidationErrorCode.INVALID_INPUT);
    }

    private DeliverySupport.ResolvedLine resolved(int index, String quantity) {
        return new DeliverySupport.ResolvedLine(index, line, item, warehouse, lot, new BigDecimal(quantity));
    }

    private MaterialReservation hold(UUID salesOrderLineId, String remaining) {
        return MaterialReservation.builder().reservationId(UUID.randomUUID()).salesOrderId(order.getSalesOrderId())
                .salesOrderLineId(salesOrderLineId).item(item).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal(remaining)).consumedQuantity(BigDecimal.ZERO).build();
    }

    private static DeliveryLineRepository.LineQuantityProjection sum(SalesOrderLine line, String quantity) {
        return new DeliveryLineRepository.LineQuantityProjection() {
            @Override
            public UUID getSalesOrderLineId() {
                return line.getSalesOrderLineId();
            }

            @Override
            public BigDecimal getQuantity() {
                return new BigDecimal(quantity);
            }
        };
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
