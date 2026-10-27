package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentAllocationRequest;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentAllocationRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * G10d: the rules a payment is checked against on save and again at post (FE R16–R18). Paid and claimed amounts are
 * always summed from the allocations themselves — P (POSTED payments) and Cp (DRAFT, the edited one excluded).
 */
@Component
@RequiredArgsConstructor
public class CustomerPaymentSupport {

    private final CustomerPaymentAllocationRepository allocationRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final SalesOrderCommercialLookupService salesOrderLookupService;
    private final OrganizationLookupService organizationLookupService;
    private final EntityManager entityManager;

    /** One allocation and the POSTED invoice it pays. */
    record ResolvedAllocation(int index, SalesInvoice invoice, BigDecimal amount) {
        String field(String name) {
            return "allocations[" + index + "]." + name;
        }
    }

    /**
     * Locks the invoices in one stable order, then re-reads them: a guard may have loaded them before the lock (B194),
     * and an entity already in the context would otherwise keep its old balance-relevant state.
     */
    Map<UUID, SalesInvoice> lockInvoices(Collection<UUID> invoiceIds) {
        List<UUID> ids = invoiceIds.stream().distinct().toList();
        invoiceRepository.lockRows(ids);
        Map<UUID, SalesInvoice> invoices = new HashMap<>();
        for (SalesInvoice invoice : invoiceRepository.findAllById(ids)) {
            entityManager.refresh(invoice);
            invoices.put(invoice.getInvoiceId(), invoice);
        }
        return invoices;
    }

    /**
     * Each allocation names a distinct POSTED invoice of this customer and of the payment's plant (FE R16). A version
     * the user did not see is a conflict ({@code expectedInvoiceVersion}, save only — at post the balances are what
     * matter, and they are re-read under the lock).
     */
    List<ResolvedAllocation> resolve(UUID customerId, UUID plantId, List<CustomerPaymentAllocationRequest> requests,
                                     Map<UUID, SalesInvoice> invoices, boolean checkVersions) {
        Set<UUID> seen = new HashSet<>();
        List<ResolvedAllocation> resolved = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            CustomerPaymentAllocationRequest request = requests.get(i);
            String field = "allocations[" + i + "].invoiceId";
            if (!seen.add(request.invoiceId())) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of(field, "appears more than once"));
            }
            SalesInvoice invoice = invoices.get(request.invoiceId());
            if (invoice == null) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of(field, "is not an invoice"));
            }
            if (!invoice.getCustomerId().equals(customerId)) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                        Map.of(field, "belongs to another customer"));
            }
            if (plantId != null && !invoice.getPlantId().equals(plantId)) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                        Map.of(field, "belongs to another plant than the payment's"));
            }
            if (!invoice.isPosted()) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.STATE_CONFLICT,
                        Map.of(field, "invoice " + invoice.getCode() + " is " + invoice.getStatus() + ", not POSTED"));
            }
            if (checkVersions && !Objects.equals(invoice.getVersion(), request.expectedInvoiceVersion())) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.CONCURRENT_MODIFICATION,
                        Map.of("allocations[" + i + "].expectedInvoiceVersion",
                                "invoice " + invoice.getCode() + " is at version " + invoice.getVersion() + "; reload it"));
            }
            resolved.add(new ResolvedAllocation(i, invoice, request.amount()));
        }
        return resolved;
    }

    /** FE R16/A28: the header amount is exactly the sum of the allocations. */
    static void ensureAmountMatches(BigDecimal amount, List<CustomerPaymentAllocationRequest> allocations) {
        BigDecimal sum = allocations.stream().map(CustomerPaymentAllocationRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(amount) != 0) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("amount",
                    "must equal the sum of the allocations (" + sum.toPlainString() + ")"));
        }
    }

    void ensureCustomerActive(UUID customerId) {
        Customer customer = salesOrderLookupService.findCustomer(customerId)
                .orElseThrow(() -> ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                        Map.of("customerId", "is not a customer")));
        if (!customer.isActive()) {
            throw ExceptionFactory.custom(BusinessErrorCode.RESOURCE_INACTIVE, "Customer " + customer.getCode() + " is inactive");
        }
    }

    /** FE R27: not after the plant's today, not before any allocated invoice. */
    void ensureDate(UUID plantId, LocalDate documentDate, List<ResolvedAllocation> allocations) {
        LocalDate today = LocalDate.now(ZoneId.of(organizationLookupService.getPlant(plantId).getTimezone()));
        if (documentDate.isAfter(today)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("documentDate", "must not be after the plant's today " + today));
        }
        LocalDate latest = allocations.stream().map(a -> a.invoice().getDocumentDate())
                .max(LocalDate::compareTo).orElse(documentDate);
        if (documentDate.isBefore(latest)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("documentDate", "must not be before the latest allocated invoice date " + latest));
        }
    }

    /** FE R17/A27/A31: per invoice, amount − P − Cp (the edited payment excluded from Cp). */
    void ensureWithinOutstanding(List<ResolvedAllocation> allocations, UUID excludePaymentId) {
        List<UUID> ids = allocations.stream().map(a -> a.invoice().getInvoiceId()).toList();
        Map<UUID, BigDecimal> paid = paid(ids);
        Map<UUID, BigDecimal> drafts = draftClaims(ids, excludePaymentId);
        for (ResolvedAllocation allocation : allocations) {
            UUID id = allocation.invoice().getInvoiceId();
            BigDecimal available = allocation.invoice().getAmount()
                    .subtract(paid.getOrDefault(id, BigDecimal.ZERO))
                    .subtract(drafts.getOrDefault(id, BigDecimal.ZERO));
            if (allocation.amount().compareTo(available) > 0) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, Map.of(allocation.field("amount"),
                        "invoice " + allocation.invoice().getCode() + " can still be allocated "
                                + available.max(BigDecimal.ZERO).toPlainString() + " (outstanding minus other draft payments)"));
            }
        }
    }

    /** P per invoice (absent = nothing paid). */
    Map<UUID, BigDecimal> paid(Collection<UUID> invoiceIds) {
        return invoiceIds.isEmpty() ? Map.of() : amounts(allocationRepository.sumPostedByInvoiceIds(invoiceIds));
    }

    /** Cp per invoice, {@code excludePaymentId} left out. */
    Map<UUID, BigDecimal> draftClaims(Collection<UUID> invoiceIds, UUID excludePaymentId) {
        return invoiceIds.isEmpty() ? Map.of()
                : amounts(allocationRepository.sumDraftByInvoiceIds(invoiceIds, excludePaymentId));
    }

    private static Map<UUID, BigDecimal> amounts(List<CustomerPaymentAllocationRepository.InvoiceAmountProjection> rows) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (CustomerPaymentAllocationRepository.InvoiceAmountProjection row : rows) {
            result.put(row.getInvoiceId(), row.getAmount());
        }
        return result;
    }
}
