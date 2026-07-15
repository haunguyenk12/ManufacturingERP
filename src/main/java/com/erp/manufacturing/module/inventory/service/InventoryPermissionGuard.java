package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
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
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasItemAccess(Authentication authentication, String permissionCode, UUID itemId) {
        if (itemId == null) {
            return false;
        }
        return itemRepository.findById(itemId)
                .map(item -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "COMPANY",
                        item.getCompany().getCompanyId()))
                .orElse(false);
    }
}
