package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * G10c (FE Q26): an invoice belongs to its plant. Reading needs any one of READ, MANAGE or POST — POST does not imply
 * MANAGE. An id that does not exist is "no access" (403), like every aggregate guard of this codebase (§0.50 #9).
 */
@Component("salesInvoicePermissionGuard")
@RequiredArgsConstructor
public class SalesInvoicePermissionGuard {

    static final String READ = "PERM_SALES_INVOICE_READ";
    static final String MANAGE = "PERM_SALES_INVOICE_MANAGE";
    static final String POST = "PERM_SALES_INVOICE_POST";
    static final List<String> ANY = List.of(READ, MANAGE, POST);

    private final PermissionGuard permissionGuard;
    private final SalesInvoiceRepository invoiceRepository;
    private final SalesOrderCommercialLookupService salesOrderLookupService;

    @Transactional(readOnly = true)
    public boolean hasInvoiceAccess(Authentication authentication, String permissionCode, UUID invoiceId) {
        return invoiceId != null && invoiceRepository.findById(invoiceId)
                .map(invoice -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "PLANT", invoice.getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canReadInvoice(Authentication authentication, UUID invoiceId) {
        return invoiceId != null && invoiceRepository.findById(invoiceId)
                .map(invoice -> canReadPlant(authentication, invoice.getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canReadPlant(Authentication authentication, UUID plantId) {
        return plantId != null && ANY.stream()
                .anyMatch(code -> permissionGuard.hasResourceAccess(authentication, code, "PLANT", plantId));
    }

    /** Invoice candidates of a sales order: invoice authority on the order's plant. */
    @Transactional(readOnly = true)
    public boolean canReadOrder(Authentication authentication, UUID salesOrderId) {
        return salesOrderLookupService.findOrder(salesOrderId)
                .map(order -> canReadPlant(authentication, order.getPlant().getPlantId()))
                .orElse(false);
    }

    /** Previewing or creating an invoice for a sales order: {@code PERM_SALES_INVOICE_MANAGE} on the order's plant. */
    @Transactional(readOnly = true)
    public boolean canManageOrder(Authentication authentication, UUID salesOrderId) {
        return salesOrderLookupService.findOrder(salesOrderId)
                .map(order -> permissionGuard.hasResourceAccess(authentication, MANAGE, "PLANT",
                        order.getPlant().getPlantId()))
                .orElse(false);
    }
}
