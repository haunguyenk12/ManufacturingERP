package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * G10a (D6): whether the current caller may see sales prices on a plant — price managers and anyone with invoice
 * authority (FE §1.3: "Price chỉ price-manage/invoice-authority"). A storekeeper who can read the order but none of
 * these sees the same order without its money. Evaluated once per response, never per line.
 */
@Component
@RequiredArgsConstructor
public class SalesPriceVisibility {

    static final List<String> PRICE_PERMISSIONS = List.of(
            "PERM_SALES_PRICE_MANAGE",
            "PERM_SALES_INVOICE_READ",
            "PERM_SALES_INVOICE_MANAGE",
            "PERM_SALES_INVOICE_POST");

    private final PermissionGuard permissionGuard;

    public boolean canSeePrices(UUID plantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || plantId == null) {
            return false;
        }
        return PRICE_PERMISSIONS.stream()
                .anyMatch(code -> permissionGuard.hasResourceAccess(authentication, code, "PLANT", plantId));
    }
}
