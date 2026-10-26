package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.domain.SalesInvoiceLine;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCancelRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCreateRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePostRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewLineResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceUpdateRequest;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceLineRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.sales.domain.CommercialCommand;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.dto.CommercialCommandResult;
import com.erp.manufacturing.module.sales.service.CommercialIdempotency;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
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
 * G10c: sales invoices — a DRAFT claims quantity on POSTED delivery lines, POST makes it a receivable. No stock
 * effect, ever (FE R15). Every command: {@code Idempotency-Key} replay first, then the order lock, then version, state
 * and the rules of {@link SalesInvoiceSupport}, then the write, all in one transaction.
 *
 * <p>Concurrency: every command that claims or bills an order's delivery lines locks the sales order row first, so two
 * drafts or two posts on the same order queue instead of both reading the same quota or billing base. Invoices never
 * move the order's version: they change no field of the order.
 *
 * <p>Money is priced by the server, never taken from the client: save prices the lines on today's base and refuses
 * with {@code PRICE_STALE} unless the client confirmed that very base ({@code billingSnapshotVersion}); post re-prices
 * the stored lines and refuses the same way if the base moved since the save (FE Q22, A22, A23).
 */
@Service
@RequiredArgsConstructor
public class SalesInvoiceService {

    private final SalesInvoiceRepository invoiceRepository;
    private final SalesInvoiceLineRepository invoiceLineRepository;
    private final SalesInvoiceSupport support;
    private final SalesOrderCommercialLookupService salesOrderLookupService;
    private final CommercialIdempotency idempotency;
    private final SecurityAuditorAware auditorAware;
    private final EntityManager entityManager;

    /** Prices the lines on today's base; holds nothing, saves nothing (FE §3.5). */
    @Transactional(readOnly = true)
    @PreAuthorize("@salesInvoicePermissionGuard.canManageOrder(authentication, #request.salesOrderId())")
    public SalesInvoicePreviewResponse preview(SalesInvoicePreviewRequest request) {
        SalesOrder order = salesOrderLookupService.getOrder(request.salesOrderId());
        ensureOrderVersion(order, request.expectedOrderVersion());
        support.ensureInvoiceable(order);
        List<SalesInvoiceSupport.ResolvedLine> lines = support.resolve(order, request.lines());
        support.ensureQuota(lines, request.editingInvoiceId());
        support.ensurePrices(lines);
        SalesInvoiceSupport.Pricing pricing = support.price(order, lines);
        List<SalesInvoicePreviewLineResponse> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            SalesInvoiceSupport.ResolvedLine line = lines.get(i);
            InvoicePricing.Priced priced = pricing.lines().get(i);
            rows.add(new SalesInvoicePreviewLineResponse(line.deliveryLine().getDeliveryLineId(),
                    line.orderLine().getSalesOrderLineId(), line.quantity(), line.orderLine().getUnitPrice(),
                    priced.amount(), priced.billedQuantityBefore(), priced.billedAmountBefore()));
        }
        return new SalesInvoicePreviewResponse(order.getSalesOrderId(), SalesInvoiceResponse.VND,
                order.pricingVersion(), pricing.snapshot(), pricing.amount(), rows);
    }

    @Transactional
    @PreAuthorize("@salesInvoicePermissionGuard.canManageOrder(authentication, #request.salesOrderId())")
    @Auditable(action = AuditAction.SALES_INVOICE_CREATED, entityType = "SalesInvoice", entityIdExpression = "#result.documentId().toString()")
    public CommercialCommandResult create(SalesInvoiceCreateRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(null, request);
        var replayed = idempotency.replay(key, CommercialCommand.INVOICE_CREATE, fingerprint);
        if (replayed.isPresent()) {
            return result(findInvoice(replayed.get()));
        }
        SalesOrder order = salesOrderLookupService.lockOrder(request.salesOrderId());
        ensureOrderVersion(order, request.expectedOrderVersion());
        support.ensureInvoiceable(order);
        List<SalesInvoiceSupport.ResolvedLine> lines = support.resolve(order, request.lines());
        support.ensureDates(order, request.documentDate(), request.dueDate(), lines);
        support.ensureQuota(lines, null);
        support.ensurePrices(lines);
        SalesInvoiceSupport.Pricing pricing = confirmed(support.price(order, lines), request.billingSnapshotVersion());

        SalesInvoice invoice = invoiceRepository.saveAndFlush(SalesInvoice.builder()
                .code(String.format("INV-%06d", invoiceRepository.nextNumber()))
                .companyId(order.getCompany().getCompanyId())
                .plantId(order.getPlant().getPlantId())
                .salesOrderId(order.getSalesOrderId())
                .customerId(order.getCustomerId())
                .customerName(order.getCustomerName())
                .documentDate(request.documentDate())
                .dueDate(request.dueDate())
                .note(trimToNull(request.note()))
                .amount(pricing.amount())
                .billingSnapshot(pricing.snapshot())
                .build());
        saveLines(invoice, lines, pricing);
        idempotency.record(key, CommercialCommand.INVOICE_CREATE, invoice.getInvoiceId(), fingerprint);
        return result(invoice);
    }

    /** Full replacement of a DRAFT, priced again; its own claim is excluded from Ci. */
    @Transactional
    @PreAuthorize("@salesInvoicePermissionGuard.hasInvoiceAccess(authentication, 'PERM_SALES_INVOICE_MANAGE', #invoiceId)")
    @Auditable(action = AuditAction.SALES_INVOICE_UPDATED, entityType = "SalesInvoice", entityIdExpression = "#invoiceId.toString()")
    public CommercialCommandResult update(UUID invoiceId, SalesInvoiceUpdateRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(invoiceId, request);
        if (idempotency.replay(key, CommercialCommand.INVOICE_UPDATE, fingerprint).isPresent()) {
            return result(findInvoice(invoiceId));
        }
        SalesInvoice invoice = lockedDraft(invoiceId, request.expectedVersion(), "edit");
        SalesOrder order = salesOrderLookupService.getOrder(invoice.getSalesOrderId());
        support.ensureInvoiceable(order);
        List<SalesInvoiceSupport.ResolvedLine> lines = support.resolve(order, request.lines());
        support.ensureDates(order, request.documentDate(), request.dueDate(), lines);
        support.ensureQuota(lines, invoiceId);
        support.ensurePrices(lines);
        SalesInvoiceSupport.Pricing pricing = confirmed(support.price(order, lines), request.billingSnapshotVersion());

        invoice.setDocumentDate(request.documentDate());
        invoice.setDueDate(request.dueDate());
        invoice.setNote(trimToNull(request.note()));
        invoice.setAmount(pricing.amount());
        invoice.setBillingSnapshot(pricing.snapshot());
        // Lines live in their own table: dirty the header before the bulk delete's automatic flush, so the version
        // moves exactly once (B112, B212).
        invoice.setUpdatedAt(Instant.now());
        invoiceLineRepository.deleteByInvoiceId(invoiceId);
        saveLines(invoice, lines, pricing);
        SalesInvoice saved = invoiceRepository.saveAndFlush(invoice);
        idempotency.record(key, CommercialCommand.INVOICE_UPDATE, invoiceId, fingerprint);
        return result(saved);
    }

    /**
     * FE R15: re-reads the source lines and re-prices them under the order lock. If the base moved since the save, or
     * the stored amounts are not what the server computes, nothing is posted ({@code PRICE_STALE}). Whoever has
     * {@code PERM_SALES_INVOICE_POST} may post, the author included (FE Q28).
     */
    @Transactional
    @PreAuthorize("@salesInvoicePermissionGuard.hasInvoiceAccess(authentication, 'PERM_SALES_INVOICE_POST', #invoiceId)")
    @Auditable(action = AuditAction.SALES_INVOICE_POSTED, entityType = "SalesInvoice", entityIdExpression = "#invoiceId.toString()")
    public CommercialCommandResult post(UUID invoiceId, SalesInvoicePostRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(invoiceId, request);
        if (idempotency.replay(key, CommercialCommand.INVOICE_POST, fingerprint).isPresent()) {
            return result(findInvoice(invoiceId));
        }
        SalesInvoice invoice = findInvoice(invoiceId);
        SalesOrder order = salesOrderLookupService.lockOrder(invoice.getSalesOrderId());
        entityManager.refresh(invoice);
        ensureVersion(invoice, request.expectedVersion());
        ensureDraft(invoice);
        support.ensureInvoiceable(order);
        List<SalesInvoiceLine> stored = invoiceLineRepository.findByInvoiceInvoiceIdOrderByLineNo(invoiceId);
        List<SalesInvoiceSupport.ResolvedLine> lines = support.resolveStored(order, stored);
        support.ensureQuota(lines, invoiceId);
        support.ensurePrices(lines);
        SalesInvoiceSupport.Pricing pricing = support.price(order, lines);
        if (!pricing.snapshot().equals(invoice.getBillingSnapshot()) || !sameAmounts(stored, pricing)) {
            throw ExceptionFactory.custom(BusinessErrorCode.PRICE_STALE,
                    "The billing base changed since this invoice was saved; preview it, save it again and confirm");
        }
        invoice.post(Instant.now().truncatedTo(ChronoUnit.MICROS), currentActor());
        SalesInvoice saved = invoiceRepository.saveAndFlush(invoice);
        idempotency.record(key, CommercialCommand.INVOICE_POST, invoiceId, fingerprint);
        return result(saved);
    }

    /** A DRAFT only, by its author, with a reason; returns the claim once. POSTED is final (FE Q33). */
    @Transactional
    @PreAuthorize("@salesInvoicePermissionGuard.hasInvoiceAccess(authentication, 'PERM_SALES_INVOICE_MANAGE', #invoiceId)")
    @Auditable(action = AuditAction.SALES_INVOICE_CANCELLED, entityType = "SalesInvoice", entityIdExpression = "#invoiceId.toString()")
    public CommercialCommandResult cancel(UUID invoiceId, SalesInvoiceCancelRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(invoiceId, request);
        if (idempotency.replay(key, CommercialCommand.INVOICE_CANCEL, fingerprint).isPresent()) {
            return result(findInvoice(invoiceId));
        }
        String reason = ReasonRules.lengthChecked(request.reason(), "reason");
        SalesInvoice invoice = lockedDraft(invoiceId, request.expectedVersion(), "cancel");
        invoice.cancel(Instant.now().truncatedTo(ChronoUnit.MICROS), currentActor(), reason);
        SalesInvoice saved = invoiceRepository.saveAndFlush(invoice);
        idempotency.record(key, CommercialCommand.INVOICE_CANCEL, invoiceId, fingerprint);
        return result(saved);
    }

    /** The client must have confirmed exactly the base the server prices on now (FE Q22). */
    private static SalesInvoiceSupport.Pricing confirmed(SalesInvoiceSupport.Pricing pricing, String billingSnapshotVersion) {
        if (!pricing.snapshot().equals(billingSnapshotVersion.trim())) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PRICE_STALE, Map.of("billingSnapshotVersion",
                    "the billing base is now " + pricing.snapshot() + "; preview again and confirm the new amounts"));
        }
        return pricing;
    }

    private static boolean sameAmounts(List<SalesInvoiceLine> stored, SalesInvoiceSupport.Pricing pricing) {
        for (int i = 0; i < stored.size(); i++) {
            if (stored.get(i).getAmount().compareTo(pricing.lines().get(i).amount()) != 0) {
                return false;
            }
        }
        return true;
    }

    private void saveLines(SalesInvoice invoice, List<SalesInvoiceSupport.ResolvedLine> lines,
                           SalesInvoiceSupport.Pricing pricing) {
        List<SalesInvoiceLine> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            SalesInvoiceSupport.ResolvedLine line = lines.get(i);
            InvoicePricing.Priced priced = pricing.lines().get(i);
            rows.add(SalesInvoiceLine.builder()
                    .invoice(invoice)
                    .lineNo(i + 1)
                    .deliveryLineId(line.deliveryLine().getDeliveryLineId())
                    .salesOrderLineId(line.orderLine().getSalesOrderLineId())
                    .quantity(line.quantity())
                    .unitPrice(line.orderLine().getUnitPrice())
                    .amount(priced.amount())
                    .billedQuantityBefore(priced.billedQuantityBefore())
                    .billedAmountBefore(priced.billedAmountBefore())
                    .build());
        }
        invoiceLineRepository.saveAll(rows);
    }

    /** Locks the order first (quota and base are per order), then re-reads the invoice the guard may have loaded. */
    private SalesInvoice lockedDraft(UUID invoiceId, Long expectedVersion, String action) {
        SalesInvoice invoice = findInvoice(invoiceId);
        salesOrderLookupService.lockOrder(invoice.getSalesOrderId());
        entityManager.refresh(invoice);
        ensureVersion(invoice, expectedVersion);
        ensureDraft(invoice);
        UUID actor = currentActor();
        if (actor == null || !actor.equals(invoice.getCreatedBy())) {
            throw ExceptionFactory.custom(AuthErrorCode.ACCESS_DENIED,
                    "Only the person who created the invoice can " + action + " it");
        }
        return invoice;
    }

    private static void ensureOrderVersion(SalesOrder order, Long expectedOrderVersion) {
        if (!Objects.equals(order.getVersion(), expectedOrderVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Sales order version is " + order.getVersion() + ", not " + expectedOrderVersion + "; reload it");
        }
    }

    private static void ensureVersion(SalesInvoice invoice, Long expectedVersion) {
        if (!Objects.equals(invoice.getVersion(), expectedVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Invoice version is " + invoice.getVersion() + ", not " + expectedVersion + "; reload it");
        }
    }

    private static void ensureDraft(SalesInvoice invoice) {
        if (!invoice.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Invoice " + invoice.getCode() + " is " + invoice.getStatus() + "; only a DRAFT can change");
        }
    }

    private SalesInvoice findInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales invoice", invoiceId));
    }

    private UUID currentActor() {
        return auditorAware.getCurrentAuditor().orElse(null);
    }

    private static CommercialCommandResult result(SalesInvoice invoice) {
        return CommercialCommandResult.of(invoice.getInvoiceId(), SalesInvoiceResponse.TYPE, invoice.getVersion());
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
