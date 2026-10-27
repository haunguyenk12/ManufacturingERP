package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus;
import com.erp.manufacturing.module.billing.dto.FulfillmentSummaryResponse;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceLineRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.sales.service.SalesPriceVisibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** G10d (FE R24): each progress figure is its own fact; prices and receivable only for whoever may see them. */
class FulfillmentSummaryServiceTest {

    private final SalesOrderCommercialLookupService orders = mock(SalesOrderCommercialLookupService.class);
    private final DeliveryLookupService deliveries = mock(DeliveryLookupService.class);
    private final SalesInvoiceRepository invoices = mock(SalesInvoiceRepository.class);
    private final SalesInvoiceLineRepository invoiceLines = mock(SalesInvoiceLineRepository.class);
    private final CustomerPaymentSupport payments = mock(CustomerPaymentSupport.class);
    private final SalesPriceVisibility prices = mock(SalesPriceVisibility.class);
    private final SalesInvoicePermissionGuard invoiceGuard = mock(SalesInvoicePermissionGuard.class);
    private final FulfillmentSummaryService service = new FulfillmentSummaryService(orders, deliveries, invoices,
            invoiceLines, payments, prices, invoiceGuard);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private final UUID plantId = UUID.randomUUID();

    @Test
    void figuresAreSeparateFacts_andTheReceivableIsPostedInvoicesMinusPostedPayments() {
        SalesOrder order = order(true, "10", "100000");
        UUID lineId = order.getLines().get(0).getSalesOrderLineId();
        when(deliveries.sumDeliveredBySalesOrderLineIds(List.of(lineId))).thenReturn(Map.of(lineId, new BigDecimal("4")));
        when(deliveries.sumDraftBySalesOrderLineIds(List.of(lineId))).thenReturn(Map.of(lineId, new BigDecimal("2")));
        SalesInvoiceLineRepository.BilledProjection billed = billed(lineId, "3", "300000");
        when(invoiceLines.sumPostedBySalesOrderLineIds(List.of(lineId))).thenReturn(List.of(billed));
        SalesInvoice invoice = SalesInvoice.builder().invoiceId(UUID.randomUUID()).amount(new BigDecimal("300000")).build();
        when(invoices.findBySalesOrderIdAndStatus(order.getSalesOrderId(), SalesInvoiceStatus.POSTED)).thenReturn(List.of(invoice));
        when(payments.paid(List.of(invoice.getInvoiceId()))).thenReturn(Map.of(invoice.getInvoiceId(), new BigDecimal("100000")));
        when(prices.canSeePrices(plantId)).thenReturn(true);
        when(invoiceGuard.canReadPlant(any(), eq(plantId))).thenReturn(true);

        FulfillmentSummaryResponse summary = service.summarize(order.getSalesOrderId());

        assertThat(summary.deliveryProgress()).isEqualTo("PARTIALLY_DELIVERED");
        var line = summary.lines().get(0);
        assertThat(line.qcAcceptedQuantity()).isEqualByComparingTo("6");
        assertThat(line.deliveredQuantity()).isEqualByComparingTo("4");
        assertThat(line.remainingToDeliverQuantity()).isEqualByComparingTo("6");
        assertThat(line.draftDeliveryClaim()).isEqualByComparingTo("2");
        assertThat(line.invoicedQuantity()).isEqualByComparingTo("3");
        assertThat(line.commercial().unitPrice()).isEqualByComparingTo("100000");
        assertThat(line.commercial().invoicedAmount()).isEqualByComparingTo("300000");
        assertThat(summary.commercial().invoicedAmount()).isEqualByComparingTo("300000");
        assertThat(summary.commercial().paidAmount()).isEqualByComparingTo("100000");
        assertThat(summary.commercial().outstandingAmount()).isEqualByComparingTo("200000");
    }

    @Test
    void withoutPriceOrInvoiceAuthority_moneyIsAbsentOnTheWire_notNull() throws Exception {
        SalesOrder order = order(true, "10", "100000");
        when(prices.canSeePrices(plantId)).thenReturn(false);
        when(invoiceGuard.canReadPlant(any(), eq(plantId))).thenReturn(false);

        JsonNode body = json.valueToTree(service.summarize(order.getSalesOrderId()));

        assertThat(body.has("commercial")).isFalse();
        assertThat(body.get("lines").get(0).has("commercial")).isFalse();
        assertThat(body.get("deliveryProgress").asText()).isEqualTo("NOT_DELIVERED");
        verifyNoInteractions(payments);
        verify(invoices, never()).findBySalesOrderIdAndStatus(any(), any());
    }

    @Test
    void aLegacyOrderWithoutBaseline_reportsNullFigures_andReadsNoLedger() {
        SalesOrder order = order(false, "10", "100000");
        when(prices.canSeePrices(plantId)).thenReturn(true);
        when(invoiceGuard.canReadPlant(any(), eq(plantId))).thenReturn(true);

        FulfillmentSummaryResponse summary = service.summarize(order.getSalesOrderId());

        assertThat(summary.deliveryProgress()).isNull();
        assertThat(summary.deliveryBaselineUnknownReason()).isNotNull();
        assertThat(summary.lines().get(0).deliveredQuantity()).isNull();
        assertThat(summary.lines().get(0).invoicedQuantity()).isNull();
        assertThat(summary.commercial().invoicedAmount()).isNull();
        verifyNoInteractions(deliveries, invoiceLines, payments);
    }

    private SalesOrder order(boolean baselineKnown, String ordered, String unitPrice) {
        Plant plant = Plant.builder().plantId(plantId).build();
        SalesOrder order = SalesOrder.builder().salesOrderId(UUID.randomUUID()).plant(plant).orderNo("SO-1")
                .status(SalesOrderStatus.IN_PRODUCTION).deliveryBaselineKnown(baselineKnown).build();
        Item item = Item.builder().itemId(UUID.randomUUID()).code("FG-1").name("Bike").unit("PCS").build();
        order.getLines().add(SalesOrderLine.builder().salesOrderLineId(UUID.randomUUID()).salesOrder(order).lineNo(1)
                .item(item).orderedQuantity(new BigDecimal(ordered)).fulfilledQuantity(new BigDecimal("6"))
                .unitPrice(new BigDecimal(unitPrice)).build());
        when(orders.getOrder(order.getSalesOrderId())).thenReturn(order);
        return order;
    }

    private static SalesInvoiceLineRepository.BilledProjection billed(UUID refId, String quantity, String amount) {
        SalesInvoiceLineRepository.BilledProjection row = mock(SalesInvoiceLineRepository.BilledProjection.class);
        when(row.getRefId()).thenReturn(refId);
        when(row.getQuantity()).thenReturn(new BigDecimal(quantity));
        when(row.getAmount()).thenReturn(new BigDecimal(amount));
        return row;
    }
}
