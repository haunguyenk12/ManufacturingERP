package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceLine;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceLineRequest;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceLineRepository;
import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.domain.DeliveryStatus;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * G10c: the rules an invoice is checked against on preview, save and again at post (FE R11–R14). Billed and claimed
 * quantities are always summed from the invoice lines themselves — B (POSTED) and Ci (DRAFT, the edited one excluded).
 */
@Component
@RequiredArgsConstructor
public class SalesInvoiceSupport {

    static final Set<SalesOrderStatus> INVOICEABLE = EnumSet.of(SalesOrderStatus.CONFIRMED,
            SalesOrderStatus.IN_PRODUCTION, SalesOrderStatus.PARTIALLY_FULFILLED, SalesOrderStatus.FULFILLED);

    private final SalesInvoiceLineRepository invoiceLineRepository;
    private final DeliveryLookupService deliveryLookupService;
    private final SalesOrderCommercialLookupService salesOrderLookupService;

    /** One line, its POSTED delivery line and its order line. */
    record ResolvedLine(int index, DeliveryLine deliveryLine, SalesOrderLine orderLine, BigDecimal quantity) {
        String field(String name) {
            return "lines[" + index + "]." + name;
        }
    }

    /** The priced lines, their total and the base they were priced on. */
    record Pricing(List<InvoicePricing.Priced> lines, BigDecimal amount, String snapshot) {
    }

    /** {@code null} when the order can be invoiced; otherwise why not. */
    String blockedReason(SalesOrder order) {
        if (!INVOICEABLE.contains(order.getStatus())) {
            return "ORDER_STATUS";
        }
        if (order.getCustomerId() == null) {
            return "CUSTOMER_MISSING";
        }
        return salesOrderLookupService.findCustomer(order.getCustomerId()).filter(Customer::isActive).isPresent()
                ? null : "CUSTOMER_INACTIVE";
    }

    void ensureInvoiceable(SalesOrder order) {
        String reason = blockedReason(order);
        if (reason == null) {
            return;
        }
        switch (reason) {
            case "ORDER_STATUS" -> throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "A sales order in status " + order.getStatus() + " cannot be invoiced");
            case "CUSTOMER_INACTIVE" -> throw ExceptionFactory.custom(BusinessErrorCode.RESOURCE_INACTIVE,
                    "The order's customer is inactive");
            default -> throw ExceptionFactory.custom(BusinessErrorCode.SOURCE_UNKNOWN,
                    "This order has no master customer; it cannot be invoiced");
        }
    }

    /**
     * Each line names a distinct line of a POSTED delivery of this order (FE R11). Unknown or another order's ⇒ 400;
     * a delivery that is not POSTED ⇒ 409 {@code STATE_CONFLICT}.
     */
    List<ResolvedLine> resolve(SalesOrder order, List<SalesInvoiceLineRequest> requests) {
        Map<UUID, DeliveryLine> deliveryLines = deliveryLookupService
                .findLines(requests.stream().map(SalesInvoiceLineRequest::deliveryLineId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(DeliveryLine::getDeliveryLineId, Function.identity()));
        Map<UUID, SalesOrderLine> orderLines = orderLines(order);
        Set<UUID> seen = new HashSet<>();
        List<ResolvedLine> resolved = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            SalesInvoiceLineRequest request = requests.get(i);
            String field = "lines[" + i + "].deliveryLineId";
            if (!seen.add(request.deliveryLineId())) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of(field, "appears more than once"));
            }
            DeliveryLine line = deliveryLines.get(request.deliveryLineId());
            if (line == null || !line.getDelivery().getSalesOrderId().equals(order.getSalesOrderId())) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                        Map.of(field, "is not a delivery line of this sales order"));
            }
            if (line.getDelivery().getStatus() != DeliveryStatus.POSTED) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.STATE_CONFLICT,
                        Map.of(field, "delivery " + line.getDelivery().getCode() + " is not POSTED"));
            }
            resolved.add(new ResolvedLine(i, line, orderLines.get(line.getSalesOrderLineId()), request.quantity()));
        }
        return resolved;
    }

    /** The stored lines of a draft, read back against the current deliveries at post. */
    List<ResolvedLine> resolveStored(SalesOrder order, List<SalesInvoiceLine> lines) {
        return resolve(order, lines.stream()
                .map(line -> new SalesInvoiceLineRequest(line.getDeliveryLineId(), line.getQuantity()))
                .toList());
    }

    /** FE R12/A15: per delivery line, delivered − B − Ci (the edited invoice excluded from Ci). */
    void ensureQuota(List<ResolvedLine> lines, UUID excludeInvoiceId) {
        List<UUID> ids = lines.stream().map(l -> l.deliveryLine().getDeliveryLineId()).toList();
        Map<UUID, BigDecimal> billed = quantities(invoiceLineRepository.sumPostedByDeliveryLineIds(ids));
        Map<UUID, BigDecimal> drafts = quantities(invoiceLineRepository.sumDraftByDeliveryLineIds(ids, excludeInvoiceId));
        for (ResolvedLine line : lines) {
            UUID id = line.deliveryLine().getDeliveryLineId();
            BigDecimal remaining = line.deliveryLine().getQuantity()
                    .subtract(billed.getOrDefault(id, BigDecimal.ZERO))
                    .subtract(drafts.getOrDefault(id, BigDecimal.ZERO));
            if (line.quantity().compareTo(remaining) > 0) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, Map.of(line.field("quantity"),
                        "the delivery line can still be invoiced " + remaining.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                                + " (delivered minus invoiced minus other draft invoices)"));
            }
        }
    }

    /** FE R13/A19: a missing price is never billed as 0. */
    void ensurePrices(List<ResolvedLine> lines) {
        Map<String, String> missing = new LinkedHashMap<>();
        for (ResolvedLine line : lines) {
            if (line.orderLine().getUnitPrice() == null) {
                missing.put(line.field("deliveryLineId"), "sales order line " + line.orderLine().getLineNo() + " has no price");
            }
        }
        if (!missing.isEmpty()) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PRICE_UNKNOWN, missing);
        }
    }

    /** FE R27: not after the plant's today, not before any source delivery, due on or after the document date. */
    void ensureDates(SalesOrder order, LocalDate documentDate, LocalDate dueDate, List<ResolvedLine> lines) {
        LocalDate today = LocalDate.now(ZoneId.of(order.getPlant().getTimezone()));
        if (documentDate.isAfter(today)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("documentDate", "must not be after the plant's today " + today));
        }
        LocalDate latest = lines.stream().map(l -> l.deliveryLine().getDelivery().getDocumentDate())
                .max(LocalDate::compareTo).orElse(documentDate);
        if (documentDate.isBefore(latest)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("documentDate", "must not be before the latest source delivery date " + latest));
        }
        if (dueDate.isBefore(documentDate)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("dueDate", "must not be before the document date"));
        }
    }

    /** Prices the lines on the cumulative base of POSTED invoices, in line order (FE R14). */
    Pricing price(SalesOrder order, List<ResolvedLine> lines) {
        Set<UUID> touched = new LinkedHashSet<>();
        lines.forEach(line -> touched.add(line.orderLine().getSalesOrderLineId()));
        Map<UUID, InvoicePricing.Base> bases = new HashMap<>();
        for (SalesInvoiceLineRepository.BilledProjection row : invoiceLineRepository.sumPostedBySalesOrderLineIds(touched)) {
            bases.put(row.getRefId(), new InvoicePricing.Base(row.getQuantity(), row.getAmount()));
        }
        Map<UUID, InvoicePricing.Base> touchedBases = new HashMap<>();
        touched.forEach(id -> touchedBases.put(id, bases.getOrDefault(id, InvoicePricing.Base.NONE)));
        List<InvoicePricing.Priced> priced = InvoicePricing.price(lines.stream()
                .map(line -> new InvoicePricing.Input(line.orderLine().getSalesOrderLineId(), line.quantity(),
                        line.orderLine().getUnitPrice()))
                .toList(), touchedBases);
        BigDecimal amount = priced.stream().map(InvoicePricing.Priced::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(InvoicePricing.MAX_MONEY) > 0) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("lines", "the invoice amount exceeds " + InvoicePricing.MAX_MONEY.toPlainString()));
        }
        return new Pricing(priced, amount, InvoicePricing.snapshot(order.pricingVersion(), touchedBases));
    }

    /** Claims and billed quantities per delivery line, for the candidates screen. */
    Map<UUID, BigDecimal> billed(Collection<UUID> deliveryLineIds) {
        return quantities(invoiceLineRepository.sumPostedByDeliveryLineIds(deliveryLineIds));
    }

    Map<UUID, BigDecimal> draftClaims(Collection<UUID> deliveryLineIds, UUID excludeInvoiceId) {
        return quantities(invoiceLineRepository.sumDraftByDeliveryLineIds(deliveryLineIds, excludeInvoiceId));
    }

    private static Map<UUID, SalesOrderLine> orderLines(SalesOrder order) {
        return order.getLines().stream().collect(Collectors.toMap(SalesOrderLine::getSalesOrderLineId, Function.identity()));
    }

    private static Map<UUID, BigDecimal> quantities(List<SalesInvoiceLineRepository.BilledProjection> rows) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (SalesInvoiceLineRepository.BilledProjection row : rows) {
            result.put(row.getRefId(), row.getQuantity());
        }
        return result;
    }
}
