package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.billing.domain.CustomerPayment;
import com.erp.manufacturing.module.billing.domain.CustomerPaymentAllocation;
import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentAllocationRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentCancelRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentCreateRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentPostRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentResponse;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentUpdateRequest;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentAllocationRepository;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.sales.domain.CommercialCommand;
import com.erp.manufacturing.module.sales.dto.CommercialCommandResult;
import com.erp.manufacturing.module.sales.service.CommercialIdempotency;
import com.erp.manufacturing.module.workorder.service.ReasonRules;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * G10d: customer payments — a DRAFT claims part of the outstanding amount of POSTED invoices, POST makes it paid. No
 * bank transfer, no stock effect (FE R18). Every command: {@code Idempotency-Key} replay first, then the invoice locks
 * (one stable order), then version, state and the rules of {@link CustomerPaymentSupport}, then the write, all in one
 * transaction.
 *
 * <p>Concurrency: two payments competing for the same outstanding amount lock the same invoice rows, so the second one
 * reads the first one's claim (FE A31, A37). Posting moves the version of every invoice it pays by one (FE A25); the
 * invoice's amount and lines never change.
 */
@Service
@RequiredArgsConstructor
public class CustomerPaymentService {

    private final CustomerPaymentRepository paymentRepository;
    private final CustomerPaymentAllocationRepository allocationRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final CustomerPaymentSupport support;
    private final CommercialIdempotency idempotency;
    private final SecurityAuditorAware auditorAware;
    private final EntityManager entityManager;

    @Transactional
    @PreAuthorize("@customerPaymentPermissionGuard.canManageAllocations(authentication, #request.allocations())")
    @Auditable(action = AuditAction.CUSTOMER_PAYMENT_CREATED, entityType = "CustomerPayment", entityIdExpression = "#result.documentId().toString()")
    public CommercialCommandResult create(CustomerPaymentCreateRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(null, request);
        var replayed = idempotency.replay(key, CommercialCommand.PAYMENT_CREATE, fingerprint);
        if (replayed.isPresent()) {
            return result(findPayment(replayed.get()));
        }
        CustomerPaymentSupport.ensureAmountMatches(request.amount(), request.allocations());
        support.ensureCustomerActive(request.customerId());
        Map<UUID, SalesInvoice> invoices = support.lockInvoices(invoiceIds(request.allocations()));
        SalesInvoice first = invoices.get(request.allocations().get(0).invoiceId());
        UUID plantId = first == null ? null : first.getPlantId();
        List<CustomerPaymentSupport.ResolvedAllocation> allocations =
                support.resolve(request.customerId(), plantId, request.allocations(), invoices, true);
        support.ensureDate(plantId, request.documentDate(), allocations);
        support.ensureWithinOutstanding(allocations, null);

        CustomerPayment payment = paymentRepository.saveAndFlush(CustomerPayment.builder()
                .code(String.format("PAY-%06d", paymentRepository.nextNumber()))
                .companyId(first.getCompanyId())
                .plantId(plantId)
                .customerId(request.customerId())
                .customerName(first.getCustomerName())
                .method(request.method())
                .reference(trimToNull(request.reference()))
                .documentDate(request.documentDate())
                .note(trimToNull(request.note()))
                .amount(request.amount())
                .build());
        saveAllocations(payment, allocations);
        idempotency.record(key, CommercialCommand.PAYMENT_CREATE, payment.getPaymentId(), fingerprint);
        return result(payment);
    }

    /** Full replacement of a DRAFT; its own claim is excluded from Cp. The customer and plant do not change. */
    @Transactional
    @PreAuthorize("@customerPaymentPermissionGuard.hasPaymentAccess(authentication, 'PERM_CUSTOMER_PAYMENT_MANAGE', #paymentId)")
    @Auditable(action = AuditAction.CUSTOMER_PAYMENT_UPDATED, entityType = "CustomerPayment", entityIdExpression = "#paymentId.toString()")
    public CommercialCommandResult update(UUID paymentId, CustomerPaymentUpdateRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(paymentId, request);
        if (idempotency.replay(key, CommercialCommand.PAYMENT_UPDATE, fingerprint).isPresent()) {
            return result(findPayment(paymentId));
        }
        CustomerPaymentSupport.ensureAmountMatches(request.amount(), request.allocations());
        CustomerPayment payment = findPayment(paymentId);
        Map<UUID, SalesInvoice> invoices = support.lockInvoices(invoiceIds(request.allocations()));
        lockedDraft(payment, request.expectedVersion(), "edit");
        List<CustomerPaymentSupport.ResolvedAllocation> allocations = support.resolve(payment.getCustomerId(),
                payment.getPlantId(), request.allocations(), invoices, true);
        support.ensureDate(payment.getPlantId(), request.documentDate(), allocations);
        support.ensureWithinOutstanding(allocations, paymentId);

        payment.setMethod(request.method());
        payment.setReference(trimToNull(request.reference()));
        payment.setDocumentDate(request.documentDate());
        payment.setNote(trimToNull(request.note()));
        payment.setAmount(request.amount());
        // Allocations live in their own table: dirty the header before the bulk delete's automatic flush, so the
        // version moves exactly once (B112, B212).
        payment.setUpdatedAt(Instant.now());
        allocationRepository.deleteByPaymentId(paymentId);
        saveAllocations(payment, allocations);
        CustomerPayment saved = paymentRepository.saveAndFlush(payment);
        idempotency.record(key, CommercialCommand.PAYMENT_UPDATE, paymentId, fingerprint);
        return result(saved);
    }

    /**
     * FE R18: re-reads the invoices under their locks and re-checks every allocation against today's outstanding, then
     * posts the payment and moves each paid invoice's version by one, together. Whoever has
     * {@code PERM_CUSTOMER_PAYMENT_POST} may post, the author included (FE Q28).
     */
    @Transactional
    @PreAuthorize("@customerPaymentPermissionGuard.hasPaymentAccess(authentication, 'PERM_CUSTOMER_PAYMENT_POST', #paymentId)")
    @Auditable(action = AuditAction.CUSTOMER_PAYMENT_POSTED, entityType = "CustomerPayment", entityIdExpression = "#paymentId.toString()")
    public CommercialCommandResult post(UUID paymentId, CustomerPaymentPostRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(paymentId, request);
        if (idempotency.replay(key, CommercialCommand.PAYMENT_POST, fingerprint).isPresent()) {
            return result(findPayment(paymentId));
        }
        CustomerPayment payment = findPayment(paymentId);
        List<CustomerPaymentAllocation> stored = allocationRepository.findByPaymentPaymentIdOrderByLineNo(paymentId);
        Map<UUID, SalesInvoice> invoices = support.lockInvoices(
                stored.stream().map(CustomerPaymentAllocation::getInvoiceId).toList());
        entityManager.refresh(payment);
        ensureVersion(payment, request.expectedVersion());
        ensureDraft(payment);
        support.ensureCustomerActive(payment.getCustomerId());
        List<CustomerPaymentAllocationRequest> requests = stored.stream()
                .map(a -> new CustomerPaymentAllocationRequest(a.getInvoiceId(), null, a.getAmount())).toList();
        List<CustomerPaymentSupport.ResolvedAllocation> allocations = support.resolve(payment.getCustomerId(),
                payment.getPlantId(), requests, invoices, false);
        support.ensureWithinOutstanding(allocations, paymentId);

        Instant now = Instant.now();
        for (CustomerPaymentSupport.ResolvedAllocation allocation : allocations) {
            allocation.invoice().setUpdatedAt(now);
        }
        invoiceRepository.saveAllAndFlush(allocations.stream().map(CustomerPaymentSupport.ResolvedAllocation::invoice).toList());
        payment.post(now.truncatedTo(ChronoUnit.MICROS), currentActor());
        CustomerPayment saved = paymentRepository.saveAndFlush(payment);
        idempotency.record(key, CommercialCommand.PAYMENT_POST, paymentId, fingerprint);
        return result(saved);
    }

    /** A DRAFT only, by its author, with a reason; returns the claim once. POSTED is final (FE Q33). */
    @Transactional
    @PreAuthorize("@customerPaymentPermissionGuard.hasPaymentAccess(authentication, 'PERM_CUSTOMER_PAYMENT_MANAGE', #paymentId)")
    @Auditable(action = AuditAction.CUSTOMER_PAYMENT_CANCELLED, entityType = "CustomerPayment", entityIdExpression = "#paymentId.toString()")
    public CommercialCommandResult cancel(UUID paymentId, CustomerPaymentCancelRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(paymentId, request);
        if (idempotency.replay(key, CommercialCommand.PAYMENT_CANCEL, fingerprint).isPresent()) {
            return result(findPayment(paymentId));
        }
        String reason = ReasonRules.lengthChecked(request.reason(), "reason");
        CustomerPayment payment = findPayment(paymentId);
        lockedDraft(payment, request.expectedVersion(), "cancel");
        payment.cancel(Instant.now().truncatedTo(ChronoUnit.MICROS), currentActor(), reason);
        CustomerPayment saved = paymentRepository.saveAndFlush(payment);
        idempotency.record(key, CommercialCommand.PAYMENT_CANCEL, paymentId, fingerprint);
        return result(saved);
    }

    private void saveAllocations(CustomerPayment payment, List<CustomerPaymentSupport.ResolvedAllocation> allocations) {
        List<CustomerPaymentAllocation> rows = new ArrayList<>();
        for (CustomerPaymentSupport.ResolvedAllocation allocation : allocations) {
            rows.add(CustomerPaymentAllocation.builder()
                    .payment(payment)
                    .lineNo(allocation.index() + 1)
                    .invoiceId(allocation.invoice().getInvoiceId())
                    .amount(allocation.amount())
                    .build());
        }
        allocationRepository.saveAll(rows);
    }

    /** Re-reads the payment the guard may have loaded (B194), then version, state and author. */
    private void lockedDraft(CustomerPayment payment, Long expectedVersion, String action) {
        entityManager.refresh(payment);
        ensureVersion(payment, expectedVersion);
        ensureDraft(payment);
        UUID actor = currentActor();
        if (actor == null || !actor.equals(payment.getCreatedBy())) {
            throw ExceptionFactory.custom(AuthErrorCode.ACCESS_DENIED,
                    "Only the person who created the payment can " + action + " it");
        }
    }

    private static List<UUID> invoiceIds(List<CustomerPaymentAllocationRequest> allocations) {
        return allocations.stream().map(CustomerPaymentAllocationRequest::invoiceId).toList();
    }

    private static void ensureVersion(CustomerPayment payment, Long expectedVersion) {
        if (!Objects.equals(payment.getVersion(), expectedVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Payment version is " + payment.getVersion() + ", not " + expectedVersion + "; reload it");
        }
    }

    private static void ensureDraft(CustomerPayment payment) {
        if (!payment.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Payment " + payment.getCode() + " is " + payment.getStatus() + "; only a DRAFT can change");
        }
    }

    private CustomerPayment findPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Customer payment", paymentId));
    }

    private UUID currentActor() {
        return auditorAware.getCurrentAuditor().orElse(null);
    }

    private static CommercialCommandResult result(CustomerPayment payment) {
        return CommercialCommandResult.of(payment.getPaymentId(), CustomerPaymentResponse.TYPE, payment.getVersion());
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
