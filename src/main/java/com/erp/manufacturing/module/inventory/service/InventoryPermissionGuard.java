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
                .map(item -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "COMPANY",
                        item.getCompany().getCompanyId()))
                .orElse(false);
    }

    /**
     * Resolves {@code lot → item → company} to a COMPANY-scoped check (C2-2). Lot detail
     * ({@code GET /inventory/lots/{lotId}}) has no {@code warehouseId} in the request to check
     * against directly — a lot can span more than one warehouse — so it is scoped one level up,
     * mirroring {@link #hasItemAccess}.
     */
    @Transactional(readOnly = true)
    public boolean hasLotAccess(Authentication authentication, String permissionCode, UUID lotId) {
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
