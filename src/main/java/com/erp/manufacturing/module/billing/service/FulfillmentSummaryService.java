package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus;
import com.erp.manufacturing.module.billing.dto.FulfillmentLineMoneyResponse;
import com.erp.manufacturing.module.billing.dto.FulfillmentLineResponse;
import com.erp.manufacturing.module.billing.dto.FulfillmentMoneyResponse;
import com.erp.manufacturing.module.billing.dto.FulfillmentSummaryResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceResponse;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceLineRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.sales.service.SalesPriceVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * G10d (FE R24): an order's QC, delivery, invoice and payment progress side by side — each its own fact. Quantities
 * for whoever reads the order; prices for price or invoice authority; the receivable (invoiced/paid/outstanding) for
 * invoice authority only. One batch query per source (C14).
 */
@Service
@RequiredArgsConstructor
public class FulfillmentSummaryService {

    private final SalesOrderCommercialLookupService salesOrderLookupService;
    private final DeliveryLookupService deliveryLookupService;
    private final SalesInvoiceRepository invoiceRepository;
    private final SalesInvoiceLineRepository invoiceLineRepository;
    private final CustomerPaymentSupport paymentSupport;
    private final SalesPriceVisibility priceVisibility;
    private final SalesInvoicePermissionGuard invoiceGuard;

    @Transactional(readOnly = true)
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_READ', #salesOrderId)")
    public FulfillmentSummaryResponse summarize(UUID salesOrderId) {
        SalesOrder order = salesOrderLookupService.getOrder(salesOrderId);
        UUID plantId = order.getPlant().getPlantId();
        boolean known = order.isDeliveryBaselineKnown();
        List<SalesOrderLine> lines = order.getLines().stream()
                .sorted(Comparator.comparing(SalesOrderLine::getLineNo)).toList();
        List<UUID> ids = lines.stream().map(SalesOrderLine::getSalesOrderLineId).toList();
        Map<UUID, BigDecimal> delivered = known ? deliveryLookupService.sumDeliveredBySalesOrderLineIds(ids) : Map.of();
        Map<UUID, BigDecimal> drafts = known ? deliveryLookupService.sumDraftBySalesOrderLineIds(ids) : Map.of();
        Map<UUID, SalesInvoiceLineRepository.BilledProjection> billed = new HashMap<>();
        if (known && !ids.isEmpty()) {
            invoiceLineRepository.sumPostedBySalesOrderLineIds(ids).forEach(row -> billed.put(row.getRefId(), row));
        }
        boolean prices = priceVisibility.canSeePrices(plantId);
        boolean receivable = invoiceGuard.canReadPlant(SecurityContextHolder.getContext().getAuthentication(), plantId);

        BigDecimal deliveredTotal = BigDecimal.ZERO;
        boolean allDelivered = true;
        List<FulfillmentLineResponse> rows = new ArrayList<>();
        for (SalesOrderLine line : lines) {
            UUID id = line.getSalesOrderLineId();
            BigDecimal d = delivered.getOrDefault(id, BigDecimal.ZERO);
            SalesInvoiceLineRepository.BilledProjection b = billed.get(id);
            deliveredTotal = deliveredTotal.add(d);
            allDelivered &= d.compareTo(line.getOrderedQuantity()) >= 0;
            rows.add(new FulfillmentLineResponse(id, line.getLineNo(), line.getItem().getItemId(),
                    line.getItem().getCode(), line.getItem().getName(), line.getItem().getUnit(),
                    line.getOrderedQuantity(), line.getFulfilledQuantity(),
                    known ? d : null,
                    known ? line.getOrderedQuantity().subtract(d).max(BigDecimal.ZERO) : null,
                    known ? drafts.getOrDefault(id, BigDecimal.ZERO) : null,
                    known ? (b == null ? BigDecimal.ZERO : b.getQuantity()) : null,
                    prices ? new FulfillmentLineMoneyResponse(line.getUnitPrice(),
                            known ? (b == null ? BigDecimal.ZERO : b.getAmount()) : null) : null));
        }
        String progress = !known ? null
                : deliveredTotal.signum() == 0 ? "NOT_DELIVERED"
                : allDelivered ? "DELIVERED" : "PARTIALLY_DELIVERED";
        return new FulfillmentSummaryResponse(order.getSalesOrderId(), order.getOrderNo(), order.getStatus().name(),
                order.getVersion(), known, known ? null : SalesOrderResponse.LEGACY_ORDER, progress, rows,
                receivable ? receivable(order, known) : null);
    }

    /** POSTED invoices of the order and the POSTED payments allocated to them; {@code null} figures while unknown. */
    private FulfillmentMoneyResponse receivable(SalesOrder order, boolean known) {
        if (!known) {
            return new FulfillmentMoneyResponse(SalesInvoiceResponse.VND, null, null, null);
        }
        List<SalesInvoice> invoices = invoiceRepository.findBySalesOrderIdAndStatus(order.getSalesOrderId(),
                SalesInvoiceStatus.POSTED);
        BigDecimal invoiced = invoices.stream().map(SalesInvoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = paymentSupport.paid(invoices.stream().map(SalesInvoice::getInvoiceId).toList()).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FulfillmentMoneyResponse(SalesInvoiceResponse.VND, invoiced, paid, invoiced.subtract(paid));
    }
}
