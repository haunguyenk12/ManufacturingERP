package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SalesOrderFulfillmentService tests")
class SalesOrderFulfillmentServiceTest {

    private static final LocalDate DUE = LocalDate.of(2026, 9, 1);

    @Mock SalesOrderRepository salesOrderRepository;
    @Mock SalesOrderLineRepository salesOrderLineRepository;

    SalesOrderFulfillmentService service;

    @BeforeEach
    void setUp() {
        service = new SalesOrderFulfillmentService(salesOrderRepository, salesOrderLineRepository);
    }

    // ── applyFulfillment + roll-up ─────────────────────────────────────────

    @Test
    void applyFulfillment_someLinesStillShort_marksThePartiallyFulfilledStatus() {
        SalesOrder order = confirmedOrderWithTwoLines("25", "40");
        stubOrderLookup(order);

        service.applyFulfillment(Map.of(lineId(order, 0), new BigDecimal("10")));

        assertThat(order.getLines().get(0).getFulfilledQuantity()).isEqualByComparingTo("10");
        assertThat(order.getLines().get(1).getFulfilledQuantity()).isEqualByComparingTo("0");
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.PARTIALLY_FULFILLED);
        verify(salesOrderRepository).saveAll(List.of(order));
    }

    @Test
    void applyFulfillment_everyLineCovered_marksTheOrderFulfilled() {
        SalesOrder order = confirmedOrderWithTwoLines("25", "40");
        order.getLines().get(0).setFulfilledQuantity(new BigDecimal("25"));
        stubOrderLookup(order);

        service.applyFulfillment(Map.of(lineId(order, 1), new BigDecimal("40")));

        assertThat(order.getLines().get(1).getFulfilledQuantity()).isEqualByComparingTo("40");
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.FULFILLED);
    }

    /** B45: {@code fulfilledQuantity} can never exceed what was ordered — surplus is free stock. */
    @Test
    void applyFulfillment_moreThanOrdered_isClippedAtOrderedQuantity() {
        SalesOrder order = confirmedOrderWithTwoLines("25", "40");
        stubOrderLookup(order);

        service.applyFulfillment(Map.of(
                lineId(order, 0), new BigDecimal("90"),
                lineId(order, 1), new BigDecimal("40")));

        assertThat(order.getLines().get(0).getFulfilledQuantity()).isEqualByComparingTo("25");
        assertThat(order.getLines().get(1).getFulfilledQuantity()).isEqualByComparingTo("40");
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.FULFILLED);
    }

    /**
     * The roll-up reads every line of the order, not only the lines being fulfilled — an order with
     * one untouched line is partially fulfilled, never complete.
     */
    @Test
    void applyFulfillment_onlyOneLineOfTwoCovered_doesNotReportTheOrderComplete() {
        SalesOrder order = confirmedOrderWithTwoLines("25", "40");
        stubOrderLookup(order);

        service.applyFulfillment(Map.of(lineId(order, 0), new BigDecimal("25")));

        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.PARTIALLY_FULFILLED);
    }

    @Test
    void applyFulfillment_nothingToApply_touchesNoRepository() {
        service.applyFulfillment(Map.of());

        verifyNoInteractions(salesOrderRepository, salesOrderLineRepository);
    }

    // ── markInProduction ───────────────────────────────────────────────────

    @Test
    void markInProduction_confirmedOrder_entersInProduction() {
        SalesOrder order = confirmedOrderWithTwoLines("25", "40");
        stubOrderLookup(order);

        service.markInProduction(List.of(lineId(order, 0)));

        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.IN_PRODUCTION);
        verify(salesOrderRepository).saveAll(List.of(order));
    }

    /** A second work order on an already-shipping order must not roll the status backwards. */
    @Test
    void markInProduction_orderAlreadyPartiallyFulfilled_keepsItsStatus() {
        SalesOrder order = confirmedOrderWithTwoLines("25", "40");
        order.markPartiallyFulfilled();
        stubOrderLookup(order);

        service.markInProduction(List.of(lineId(order, 0)));

        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.PARTIALLY_FULFILLED);
    }

    // ── findAllocationTargets ──────────────────────────────────────────────

    /** Rule C15: however many lines are asked for, exactly one repository round trip. */
    @Test
    void findAllocationTargets_readsEveryLineInOneQuery() {
        UUID firstLineId = UUID.randomUUID();
        UUID secondLineId = UUID.randomUUID();
        List<UUID> lineIds = List.of(firstLineId, secondLineId);
        when(salesOrderLineRepository.findAllocationTargets(lineIds)).thenReturn(List.of(
                new SalesOrderAllocationTarget(firstLineId, UUID.randomUUID(), "SO-001", 1, DUE,
                        "EA", new BigDecimal("25"), new BigDecimal("5")),
                new SalesOrderAllocationTarget(secondLineId, UUID.randomUUID(), "SO-002", 2, DUE,
                        "EA", new BigDecimal("40"), BigDecimal.ZERO)));

        Map<UUID, SalesOrderAllocationTarget> targets = service.findAllocationTargets(lineIds);

        assertThat(targets).hasSize(2);
        assertThat(targets.get(firstLineId).salesOrderCode()).isEqualTo("SO-001");
        assertThat(targets.get(firstLineId).openQuantity()).isEqualByComparingTo("20");
        verify(salesOrderLineRepository, times(1)).findAllocationTargets(anyCollection());
    }

    @Test
    void findAllocationTargets_noLineIds_skipsTheQuery() {
        assertThat(service.findAllocationTargets(List.of())).isEmpty();

        verifyNoInteractions(salesOrderLineRepository);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void stubOrderLookup(SalesOrder order) {
        when(salesOrderLineRepository.findOrderIdsByLineIds(anyCollection()))
                .thenReturn(List.of(order.getSalesOrderId()));
        when(salesOrderRepository.findBySalesOrderIdIn(List.of(order.getSalesOrderId())))
                .thenReturn(List.of(order));
    }

    private UUID lineId(SalesOrder order, int index) {
        return order.getLines().get(index).getSalesOrderLineId();
    }

    private SalesOrder confirmedOrderWithTwoLines(String firstQuantity, String secondQuantity) {
        Company company = Company.builder().companyId(UUID.randomUUID()).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
        SalesOrder order = SalesOrder.builder()
                .salesOrderId(UUID.randomUUID())
                .company(company)
                .orderNo("SO-001")
                .customerName("ACME Corp")
                .orderDate(DUE.minusDays(30))
                .status(SalesOrderStatus.CONFIRMED)
                .build();
        order.getLines().add(line(order, company, 1, firstQuantity));
        order.getLines().add(line(order, company, 2, secondQuantity));
        return order;
    }

    private SalesOrderLine line(SalesOrder order, Company company, int lineNo, String quantity) {
        Item item = Item.builder().itemId(UUID.randomUUID()).company(company).code("FG-" + lineNo)
                .name("Finished good " + lineNo).type(ItemType.FINISHED_GOOD).unit("EA")
                .status(ItemStatus.ACTIVE).build();
        return SalesOrderLine.builder()
                .salesOrderLineId(UUID.randomUUID())
                .salesOrder(order)
                .item(item)
                .lineNo(lineNo)
                .orderedQuantity(new BigDecimal(quantity))
                .fulfilledQuantity(BigDecimal.ZERO)
                .dueDate(DUE)
                .build();
    }
}
