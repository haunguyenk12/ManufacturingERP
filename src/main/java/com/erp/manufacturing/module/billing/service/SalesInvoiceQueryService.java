package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceLine;
import com.erp.manufacturing.module.billing.domain.PaymentState;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCandidateLineResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCandidatesResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceEventResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceLineResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceResponse;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceLineRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * G10c reads. Labels for a page come from batch lookups (C14): the invoice lines, their delivery lines, the orders and
 * their lines, usernames — one query each, never one per row.
 */
@Service
@RequiredArgsConstructor
public class SalesInvoiceQueryService {

    private final SalesInvoiceRepository invoiceRepository;
    private final SalesInvoiceLineRepository invoiceLineRepository;
    private final SalesInvoiceSupport support;
    private final CustomerPaymentSupport paymentSupport;
    private final DeliveryLookupService deliveryLookupService;
    private final SalesOrderCommercialLookupService salesOrderLookupService;
    private final UserLookupService userLookupService;
    private final PermissionGuard permissionGuard;
    private final SecurityAuditorAware auditorAware;

    @Transactional(readOnly = true)
    @PreAuthorize("@salesInvoicePermissionGuard.canReadInvoice(authentication, #invoiceId)")
    public SalesInvoiceResponse get(UUID invoiceId) {
        return toResponses(List.of(findInvoice(invoiceId))).get(0);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@salesInvoicePermissionGuard.canReadPlant(authentication, #plantId)")
    public PageResult<SalesInvoiceResponse> list(UUID plantId, SalesInvoiceStatus status, UUID salesOrderId,
                                                 UUID customerId, LocalDate fromDate, LocalDate toDate, String search,
                                                 PaymentState paymentState, Pageable pageable) {
        Page<SalesInvoice> page = invoiceRepository.search(plantId, status, salesOrderId, customerId, fromDate, toDate,
                StringUtils.hasText(search) ? search.trim() : null, paymentState == null ? null : paymentState.name(),
                pageable);
        return PageResult.from(new PageImpl<>(toResponses(page.getContent()), pageable, page.getTotalElements()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@salesInvoicePermissionGuard.canReadInvoice(authentication, #invoiceId)")
    public PageResult<SalesInvoiceEventResponse> history(UUID invoiceId, Pageable pageable) {
        SalesInvoice invoice = findInvoice(invoiceId);
        List<SalesInvoiceEventResponse> raw = Stream.of(
                        new SalesInvoiceEventResponse("CREATED", invoice.getCreatedBy(), null, null, invoice.getCreatedAt()),
                        new SalesInvoiceEventResponse("POSTED", invoice.getPostedBy(), null, null, invoice.getPostedAt()),
                        new SalesInvoiceEventResponse("CANCELLED", invoice.getCancelledBy(), null,
                                invoice.getCancelReason(), invoice.getCancelledAt()))
                .filter(event -> event.at() != null)
                .sorted(Comparator.comparing(SalesInvoiceEventResponse::at))
                .toList();
        Map<UUID, String> usernames = userLookupService.findUsernames(
                raw.stream().map(SalesInvoiceEventResponse::actorId).filter(Objects::nonNull).distinct().toList());
        List<SalesInvoiceEventResponse> rows = raw.stream()
                .map(e -> new SalesInvoiceEventResponse(e.action(), e.actorId(),
                        e.actorId() == null ? null : usernames.get(e.actorId()), e.reason(), e.at()))
                .toList();
        return page(rows, pageable);
    }

    /** FE R11/R12: the lines of the order's POSTED deliveries with B, the claims and what can still be billed. */
    @Transactional(readOnly = true)
    @PreAuthorize("@salesInvoicePermissionGuard.canReadOrder(authentication, #salesOrderId)")
    public SalesInvoiceCandidatesResponse candidates(UUID salesOrderId, UUID editingInvoiceId, Pageable pageable) {
        SalesOrder order = salesOrderLookupService.getOrder(salesOrderId);
        Map<UUID, SalesOrderLine> orderLines = order.getLines().stream()
                .collect(Collectors.toMap(SalesOrderLine::getSalesOrderLineId, Function.identity()));
        List<DeliveryLine> lines = deliveryLookupService.findPostedLines(salesOrderId);
        List<UUID> ids = lines.stream().map(DeliveryLine::getDeliveryLineId).toList();
        Map<UUID, BigDecimal> billed = ids.isEmpty() ? Map.of() : support.billed(ids);
        Map<UUID, BigDecimal> others = ids.isEmpty() ? Map.of() : support.draftClaims(ids, editingInvoiceId);
        Map<UUID, BigDecimal> own = editingInvoiceId == null ? Map.of() : ownClaims(editingInvoiceId, salesOrderId);
        List<SalesInvoiceCandidateLineResponse> rows = lines.stream().map(line -> {
            UUID id = line.getDeliveryLineId();
            SalesOrderLine orderLine = orderLines.get(line.getSalesOrderLineId());
            BigDecimal b = billed.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal other = others.getOrDefault(id, BigDecimal.ZERO);
            return new SalesInvoiceCandidateLineResponse(id, line.getDelivery().getDeliveryId(),
                    line.getDelivery().getCode(), line.getDelivery().getDocumentDate(), line.getSalesOrderLineId(),
                    orderLine.getLineNo(), line.getItemId(), orderLine.getItem().getCode(),
                    orderLine.getItem().getName(), orderLine.getItem().getUnit(), line.getQuantity(), b,
                    own.getOrDefault(id, BigDecimal.ZERO), other,
                    line.getQuantity().subtract(b).subtract(other).max(BigDecimal.ZERO),
                    orderLine.getUnitPrice(), orderLine.getUnitPrice() != null);
        }).toList();
        String blocked = support.blockedReason(order);
        return new SalesInvoiceCandidatesResponse(order.getSalesOrderId(), order.getOrderNo(), order.getStatus().name(),
                order.getVersion(), order.getCustomerId(), order.getCustomerName(), SalesInvoiceResponse.VND,
                order.pricingVersion(), blocked == null, blocked, page(rows, pageable));
    }

    private Map<UUID, BigDecimal> ownClaims(UUID invoiceId, UUID salesOrderId) {
        return invoiceRepository.findById(invoiceId)
                .filter(invoice -> invoice.isDraft() && invoice.getSalesOrderId().equals(salesOrderId))
                .map(invoice -> invoiceLineRepository.findByInvoiceInvoiceIdOrderByLineNo(invoiceId).stream()
                        .collect(Collectors.toMap(SalesInvoiceLine::getDeliveryLineId, SalesInvoiceLine::getQuantity,
                                BigDecimal::add)))
                .orElse(Map.of());
    }

    private List<SalesInvoiceResponse> toResponses(List<SalesInvoice> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<SalesInvoiceLine>> linesByInvoice = invoiceLineRepository
                .findByInvoiceInvoiceIdInOrderByLineNo(invoices.stream().map(SalesInvoice::getInvoiceId).toList())
                .stream()
                .collect(Collectors.groupingBy(line -> line.getInvoice().getInvoiceId(), LinkedHashMap::new,
                        Collectors.toList()));
        List<SalesInvoiceLine> allLines = linesByInvoice.values().stream().flatMap(List::stream).toList();
        Map<UUID, DeliveryLine> deliveryLines = deliveryLookupService
                .findLines(allLines.stream().map(SalesInvoiceLine::getDeliveryLineId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(DeliveryLine::getDeliveryLineId, Function.identity()));
        Map<UUID, SalesOrder> orders = salesOrderLookupService.findOrders(
                invoices.stream().map(SalesInvoice::getSalesOrderId).distinct().toList());
        Map<UUID, SalesOrderLine> orderLines = orders.values().stream()
                .flatMap(order -> order.getLines().stream())
                .collect(Collectors.toMap(SalesOrderLine::getSalesOrderLineId, Function.identity()));
        Map<UUID, String> usernames = userLookupService.findUsernames(invoices.stream()
                .flatMap(i -> Stream.of(i.getCreatedBy(), i.getPostedBy(), i.getCancelledBy()))
                .filter(Objects::nonNull).distinct().toList());
        Map<UUID, BigDecimal> paid = paymentSupport.paid(invoices.stream()
                .filter(SalesInvoice::isPosted).map(SalesInvoice::getInvoiceId).toList());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Map<String, Boolean> permissions = new HashMap<>();

        List<SalesInvoiceResponse> responses = new ArrayList<>();
        for (SalesInvoice invoice : invoices) {
            List<SalesInvoiceLineResponse> lineResponses = linesByInvoice.getOrDefault(invoice.getInvoiceId(), List.of())
                    .stream().map(line -> {
                        SalesOrderLine orderLine = orderLines.get(line.getSalesOrderLineId());
                        DeliveryLine source = deliveryLines.get(line.getDeliveryLineId());
                        return new SalesInvoiceLineResponse(line.getInvoiceLineId(), line.getLineNo(),
                                line.getDeliveryLineId(),
                                source == null ? null : source.getDelivery().getDeliveryId(),
                                source == null ? null : source.getDelivery().getCode(),
                                line.getSalesOrderLineId(),
                                orderLine == null ? null : orderLine.getLineNo(),
                                orderLine == null ? null : orderLine.getItem().getItemId(),
                                orderLine == null ? null : orderLine.getItem().getCode(),
                                orderLine == null ? null : orderLine.getItem().getName(),
                                orderLine == null ? null : orderLine.getItem().getUnit(),
                                line.getQuantity(), line.getUnitPrice(), line.getAmount(),
                                line.getBilledQuantityBefore(), line.getBilledAmountBefore());
                    }).toList();
            SalesOrder order = orders.get(invoice.getSalesOrderId());
            boolean posted = invoice.isPosted();
            BigDecimal paidAmount = posted ? paid.getOrDefault(invoice.getInvoiceId(), BigDecimal.ZERO) : null;
            responses.add(new SalesInvoiceResponse(invoice.getInvoiceId(), invoice.getCode(), SalesInvoiceResponse.TYPE,
                    invoice.getStatus().name(), invoice.getVersion(), invoice.getCompanyId(), invoice.getPlantId(),
                    invoice.getSalesOrderId(), order == null ? null : order.getOrderNo(), invoice.getCustomerId(),
                    invoice.getCustomerName(), invoice.getDocumentDate(), invoice.getDueDate(), invoice.getNote(),
                    SalesInvoiceResponse.VND, invoice.getAmount(), paidAmount,
                    posted ? invoice.getAmount().subtract(paidAmount) : null,
                    posted ? PaymentState.of(invoice.getAmount(), paidAmount).name() : null,
                    invoice.getBillingSnapshot(), lineResponses,
                    invoice.getCreatedBy(), usernames.get(invoice.getCreatedBy()), invoice.getCreatedAt(),
                    invoice.getPostedBy(), invoice.getPostedBy() == null ? null : usernames.get(invoice.getPostedBy()),
                    invoice.getPostedAt(),
                    invoice.getCancelledBy(),
                    invoice.getCancelledBy() == null ? null : usernames.get(invoice.getCancelledBy()),
                    invoice.getCancelledAt(), invoice.getCancelReason(),
                    allowedActions(invoice, authentication, actor, permissions)));
        }
        return responses;
    }

    /** UX hint only: EDIT/CANCEL for the author with MANAGE, POST for POST authority; each check once per response. */
    private List<String> allowedActions(SalesInvoice invoice, Authentication authentication, UUID actor,
                                        Map<String, Boolean> permissions) {
        if (!invoice.isDraft() || authentication == null) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        if (actor != null && actor.equals(invoice.getCreatedBy())
                && has(authentication, SalesInvoicePermissionGuard.MANAGE, invoice.getPlantId(), permissions)) {
            actions.add("EDIT");
            actions.add("CANCEL");
        }
        if (has(authentication, SalesInvoicePermissionGuard.POST, invoice.getPlantId(), permissions)) {
            actions.add("POST");
        }
        return actions;
    }

    private boolean has(Authentication authentication, String permission, UUID plantId, Map<String, Boolean> cache) {
        return cache.computeIfAbsent(permission + "@" + plantId,
                k -> permissionGuard.hasResourceAccess(authentication, permission, "PLANT", plantId));
    }

    private SalesInvoice findInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales invoice", invoiceId));
    }

    private static <T> PageResult<T> page(List<T> rows, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return PageResult.from(new PageImpl<>(rows.subList(from, to), pageable, rows.size()));
    }
}
