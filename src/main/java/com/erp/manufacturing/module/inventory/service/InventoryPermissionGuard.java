package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("inventoryPermissionGuard")
@RequiredArgsConstructor
public class InventoryPermissionGuard {

    private final ItemRepository itemRepository;
    private final InventoryLotRepository lotRepository;
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasItemAccess(Authentication authentication, String permissionCode, UUID itemId) {
        if (itemId == null) {
            return false;
        }
        return itemRepository.findById(itemId)
                .map(item -> permissionGuard.hasCompanyOrPlantAccess(
                        authentication,
                        permissionCode,
                        item.getCompany().getCompanyId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasItemCompanyAccess(Authentication authentication,
                                        String permissionCode,
                                        UUID companyId) {
        return permissionGuard.hasCompanyOrPlantAccess(authentication, permissionCode, companyId);
    }

    /**
     * Resolves {@code lot → item → company} for the company-wide detail mode (C2-2). The normal FE
     * flow supplies {@code warehouseId} and is authorized directly by {@code PermissionGuard}; this
     * stricter fallback is only for callers requesting balances across every warehouse.
     */
    @Transactional(readOnly = true)
    public boolean hasLotCompanyAccess(Authentication authentication, String permissionCode, UUID lotId) {
        if (lotId == null) {
            return false;
        }
        return lotRepository.findById(lotId)
                .map(lot -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "COMPANY",
                        lot.getItem().getCompany().getCompanyId()))
                .orElse(false);
    }
}
