package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.billing.domain.CustomerPayment;
import com.erp.manufacturing.module.billing.domain.CustomerPaymentAllocation;
import com.erp.manufacturing.module.billing.domain.CustomerPaymentStatus;
import com.erp.manufacturing.module.billing.domain.PaymentMethod;
import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceStatus;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentAllocationResponse;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentEventResponse;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentResponse;
import com.erp.manufacturing.module.billing.dto.InvoicePaymentResponse;
import com.erp.manufacturing.module.billing.dto.PaymentInvoiceCandidateResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceResponse;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentAllocationRepository;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
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
 * G10d payment reads. A payment shows only the minimal invoice it pays (FE Q30): code, date, order number, the
 * allocated amount — never order prices or invoice lines. Labels for a page come from batch lookups (C14).
 */
@Service
@RequiredArgsConstructor
public class CustomerPaymentQueryService {

    private final CustomerPaymentRepository paymentRepository;
    private final CustomerPaymentAllocationRepository allocationRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final CustomerPaymentSupport support;
    private final SalesOrderCommercialLookupService salesOrderLookupService;
    private final UserLookupService userLookupService;
    private final PermissionGuard permissionGuard;
    private final SecurityAuditorAware auditorAware;

    @Transactional(readOnly = true)
    @PreAuthorize("@customerPaymentPermissionGuard.canReadPayment(authentication, #paymentId)")
    public CustomerPaymentResponse get(UUID paymentId) {
        return toResponses(List.of(findPayment(paymentId))).get(0);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@customerPaymentPermissionGuard.canReadPlant(authentication, #plantId)")
    public PageResult<CustomerPaymentResponse> list(UUID plantId, CustomerPaymentStatus status, PaymentMethod method,
                                                    UUID customerId, UUID invoiceId, LocalDate fromDate, LocalDate toDate,
                                                    String search, Pageable pageable) {
        Page<CustomerPayment> page = paymentRepository.search(plantId, status, method, customerId, invoiceId, fromDate,
                toDate, StringUtils.hasText(search) ? search.trim() : null, pageable);
        return PageResult.from(new PageImpl<>(toResponses(page.getContent()), pageable, page.getTotalElements()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@customerPaymentPermissionGuard.canReadPayment(authentication, #paymentId)")
    public PageResult<CustomerPaymentEventResponse> history(UUID paymentId, Pageable pageable) {
        CustomerPayment payment = findPayment(paymentId);
        List<CustomerPaymentEventResponse> raw = Stream.of(
                        new CustomerPaymentEventResponse("CREATED", payment.getCreatedBy(), null, null, payment.getCreatedAt()),
                        new CustomerPaymentEventResponse("POSTED", payment.getPostedBy(), null, null, payment.getPostedAt()),
                        new CustomerPaymentEventResponse("CANCELLED", payment.getCancelledBy(), null,
                                payment.getCancelReason(), payment.getCancelledAt()))
                .filter(event -> event.at() != null)
                .sorted(Comparator.comparing(CustomerPaymentEventResponse::at))
                .toList();
        Map<UUID, String> usernames = userLookupService.findUsernames(
                raw.stream().map(CustomerPaymentEventResponse::actorId).filter(Objects::nonNull).distinct().toList());
        return page(raw.stream()
                .map(e -> new CustomerPaymentEventResponse(e.action(), e.actorId(),
                        e.actorId() == null ? null : usernames.get(e.actorId()), e.reason(), e.at()))
                .toList(), pageable);
    }

    /**
     * FE R17/Q30: the customer's POSTED invoices of the plant that still have something to pay, with the minimal
     * balance — amount, paid, outstanding, the claims of this draft and of others, what can still be allocated.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@customerPaymentPermissionGuard.canReadPlant(authentication, #plantId)")
    public PageResult<PaymentInvoiceCandidateResponse> invoiceCandidates(UUID plantId, UUID customerId,
                                                                        UUID editingPaymentId, Pageable pageable) {
        List<SalesInvoice> invoices = invoiceRepository.findByPlantIdAndCustomerIdAndStatusOrderByDocumentDateAscCodeAsc(
                plantId, customerId, SalesInvoiceStatus.POSTED);
        List<UUID> ids = invoices.stream().map(SalesInvoice::getInvoiceId).toList();
        Map<UUID, BigDecimal> paid = support.paid(ids);
        Map<UUID, BigDecimal> others = support.draftClaims(ids, editingPaymentId);
        Map<UUID, BigDecimal> own = editingPaymentId == null ? Map.of() : ownClaims(editingPaymentId);
        Map<UUID, SalesOrder> orders = salesOrderLookupService.findOrders(
                invoices.stream().map(SalesInvoice::getSalesOrderId).distinct().toList());
        List<PaymentInvoiceCandidateResponse> rows = new ArrayList<>();
        for (SalesInvoice invoice : invoices) {
            UUID id = invoice.getInvoiceId();
            BigDecimal outstanding = invoice.getAmount().subtract(paid.getOrDefault(id, BigDecimal.ZERO));
            if (outstanding.signum() <= 0 && !own.containsKey(id)) {
                continue;
            }
            BigDecimal other = others.getOrDefault(id, BigDecimal.ZERO);
            SalesOrder order = orders.get(invoice.getSalesOrderId());
            rows.add(new PaymentInvoiceCandidateResponse(id, invoice.getCode(), invoice.getDocumentDate(),
                    invoice.getDueDate(), invoice.getSalesOrderId(), order == null ? null : order.getOrderNo(),
                    invoice.getVersion(), SalesInvoiceResponse.VND, invoice.getAmount(),
                    paid.getOrDefault(id, BigDecimal.ZERO), outstanding, own.getOrDefault(id, BigDecimal.ZERO), other,
                    outstanding.subtract(other).max(BigDecimal.ZERO)));
        }
        return page(rows, pageable);
    }

    /** FE "relatedPayments": the payments of one invoice and only the part each allocated to it. */
    @Transactional(readOnly = true)
    @PreAuthorize("@salesInvoicePermissionGuard.canReadInvoice(authentication, #invoiceId)"
            + " and @customerPaymentPermissionGuard.canReadInvoicePayments(authentication, #invoiceId)")
    public PageResult<InvoicePaymentResponse> invoicePayments(UUID invoiceId, Pageable pageable) {
        Page<CustomerPaymentAllocation> page = allocationRepository.findByInvoiceId(invoiceId, pageable);
        return PageResult.from(page.map(a -> new InvoicePaymentResponse(a.getPayment().getPaymentId(),
                a.getPayment().getCode(), a.getPayment().getStatus().name(), a.getPayment().getDocumentDate(),
                a.getPayment().getMethod().name(), a.getAmount(), a.getPayment().getPostedAt())));
    }

    private Map<UUID, BigDecimal> ownClaims(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(CustomerPayment::isDraft)
                .map(payment -> allocationRepository.findByPaymentPaymentIdOrderByLineNo(paymentId).stream()
                        .collect(Collectors.toMap(CustomerPaymentAllocation::getInvoiceId,
                                CustomerPaymentAllocation::getAmount, BigDecimal::add)))
                .orElse(Map.of());
    }

    private List<CustomerPaymentResponse> toResponses(List<CustomerPayment> payments) {
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<CustomerPaymentAllocation>> byPayment = allocationRepository
                .findByPaymentPaymentIdInOrderByLineNo(payments.stream().map(CustomerPayment::getPaymentId).toList())
                .stream()
                .collect(Collectors.groupingBy(a -> a.getPayment().getPaymentId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, SalesInvoice> invoices = invoiceRepository.findAllById(byPayment.values().stream()
                        .flatMap(List::stream).map(CustomerPaymentAllocation::getInvoiceId).distinct().toList())
                .stream().collect(Collectors.toMap(SalesInvoice::getInvoiceId, Function.identity()));
        Map<UUID, SalesOrder> orders = salesOrderLookupService.findOrders(
                invoices.values().stream().map(SalesInvoice::getSalesOrderId).distinct().toList());
        Map<UUID, String> usernames = userLookupService.findUsernames(payments.stream()
                .flatMap(p -> Stream.of(p.getCreatedBy(), p.getPostedBy(), p.getCancelledBy()))
                .filter(Objects::nonNull).distinct().toList());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Map<String, Boolean> permissions = new HashMap<>();

        List<CustomerPaymentResponse> responses = new ArrayList<>();
        for (CustomerPayment payment : payments) {
            List<CustomerPaymentAllocationResponse> allocations = byPayment.getOrDefault(payment.getPaymentId(), List.of())
                    .stream().map(a -> {
                        SalesInvoice invoice = invoices.get(a.getInvoiceId());
                        SalesOrder order = invoice == null ? null : orders.get(invoice.getSalesOrderId());
                        return new CustomerPaymentAllocationResponse(a.getAllocationId(), a.getLineNo(), a.getInvoiceId(),
                                invoice == null ? null : invoice.getCode(),
                                invoice == null ? null : invoice.getDocumentDate(),
                                invoice == null ? null : invoice.getSalesOrderId(),
                                order == null ? null : order.getOrderNo(), a.getAmount());
                    }).toList();
            responses.add(new CustomerPaymentResponse(payment.getPaymentId(), payment.getCode(), CustomerPaymentResponse.TYPE,
                    payment.getStatus().name(), payment.getVersion(), payment.getCompanyId(), payment.getPlantId(),
                    payment.getCustomerId(), payment.getCustomerName(), payment.getDocumentDate(),
                    payment.getMethod().name(), payment.getReference(), payment.getNote(), SalesInvoiceResponse.VND,
                    payment.getAmount(), allocations,
                    payment.getCreatedBy(), usernames.get(payment.getCreatedBy()), payment.getCreatedAt(),
                    payment.getPostedBy(), payment.getPostedBy() == null ? null : usernames.get(payment.getPostedBy()),
                    payment.getPostedAt(),
                    payment.getCancelledBy(),
                    payment.getCancelledBy() == null ? null : usernames.get(payment.getCancelledBy()),
                    payment.getCancelledAt(), payment.getCancelReason(),
                    allowedActions(payment, authentication, actor, permissions)));
        }
        return responses;
    }

    /** UX hint only: EDIT/CANCEL for the author with MANAGE, POST for POST authority; each check once per response. */
    private List<String> allowedActions(CustomerPayment payment, Authentication authentication, UUID actor,
                                        Map<String, Boolean> permissions) {
        if (!payment.isDraft() || authentication == null) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        if (actor != null && actor.equals(payment.getCreatedBy())
                && has(authentication, CustomerPaymentPermissionGuard.MANAGE, payment.getPlantId(), permissions)) {
            actions.add("EDIT");
            actions.add("CANCEL");
        }
        if (has(authentication, CustomerPaymentPermissionGuard.POST, payment.getPlantId(), permissions)) {
            actions.add("POST");
        }
        return actions;
    }

    private boolean has(Authentication authentication, String permission, UUID plantId, Map<String, Boolean> cache) {
        return cache.computeIfAbsent(permission + "@" + plantId,
                k -> permissionGuard.hasResourceAccess(authentication, permission, "PLANT", plantId));
    }

    private CustomerPayment findPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Customer payment", paymentId));
    }

    private static <T> PageResult<T> page(List<T> rows, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return PageResult.from(new PageImpl<>(rows.subList(from, to), pageable, rows.size()));
    }
}
