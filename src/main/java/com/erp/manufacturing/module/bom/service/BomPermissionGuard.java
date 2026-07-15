package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import com.erp.manufacturing.module.bom.repository.BomLineRepository;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("bomPermissionGuard")
@RequiredArgsConstructor
public class BomPermissionGuard {

    private final BomHeaderRepository bomHeaderRepository;
    private final BomLineRepository bomLineRepository;
    private final ItemLookupService itemLookupService;
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasBomAccess(Authentication authentication, String permissionCode, UUID bomId) {
        if (bomId == null) {
            return false;
        }
        return bomHeaderRepository.findById(bomId)
                .map(bom -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "COMPANY",
                        bom.getCompany().getCompanyId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasBomLineAccess(Authentication authentication, String permissionCode, UUID lineId) {
        if (lineId == null) {
            return false;
        }
        return bomLineRepository.findById(lineId)
                .map(line -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "COMPANY",
                        line.getBom().getCompany().getCompanyId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasItemBomAccess(Authentication authentication, String permissionCode, UUID itemId) {
        if (itemId == null) {
            return false;
        }
        try {
            return permissionGuard.hasResourceAccess(
                    authentication,
                    permissionCode,
                    "COMPANY",
                    itemLookupService.getItem(itemId).getCompany().getCompanyId());
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
