package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentAllocationRequest;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * G10d (FE Q26): a payment belongs to its plant. Reading needs any one of READ, MANAGE or POST — POST does not imply
 * MANAGE. An id that does not exist is "no access" (403), like every aggregate guard of this codebase (§0.50 #9).
 */
@Component("customerPaymentPermissionGuard")
@RequiredArgsConstructor
public class CustomerPaymentPermissionGuard {

    static final String READ = "PERM_CUSTOMER_PAYMENT_READ";
    static final String MANAGE = "PERM_CUSTOMER_PAYMENT_MANAGE";
    static final String POST = "PERM_CUSTOMER_PAYMENT_POST";
    static final List<String> ANY = List.of(READ, MANAGE, POST);

    private final PermissionGuard permissionGuard;
    private final CustomerPaymentRepository paymentRepository;
    private final SalesInvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public boolean hasPaymentAccess(Authentication authentication, String permissionCode, UUID paymentId) {
        return paymentId != null && paymentRepository.findById(paymentId)
                .map(payment -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "PLANT", payment.getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canReadPayment(Authentication authentication, UUID paymentId) {
        return paymentId != null && paymentRepository.findById(paymentId)
                .map(payment -> canReadPlant(authentication, payment.getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canReadPlant(Authentication authentication, UUID plantId) {
        return plantId != null && ANY.stream()
                .anyMatch(code -> permissionGuard.hasResourceAccess(authentication, code, "PLANT", plantId));
    }

    /** The payments of one invoice: payment authority on the invoice's plant (FE: invoice read AND payment read). */
    @Transactional(readOnly = true)
    public boolean canReadInvoicePayments(Authentication authentication, UUID invoiceId) {
        return invoiceId != null && invoiceRepository.findById(invoiceId)
                .map(invoice -> canReadPlant(authentication, invoice.getPlantId()))
                .orElse(false);
    }

    /** Creating a payment: {@code PERM_CUSTOMER_PAYMENT_MANAGE} on the plant of every invoice it allocates to. */
    @Transactional(readOnly = true)
    public boolean canManageAllocations(Authentication authentication, List<CustomerPaymentAllocationRequest> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return false;
        }
        List<UUID> ids = allocations.stream().map(CustomerPaymentAllocationRequest::invoiceId).distinct().toList();
        List<SalesInvoice> invoices = invoiceRepository.findAllById(ids);
        return invoices.size() == ids.size() && invoices.stream().map(SalesInvoice::getPlantId).distinct()
                .allMatch(plantId -> permissionGuard.hasResourceAccess(authentication, MANAGE, "PLANT", plantId));
    }
}
