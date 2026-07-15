package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("inventorySettingPermissionGuard")
@RequiredArgsConstructor
public class InventorySettingPermissionGuard {

    private final ItemWarehouseSettingRepository settingRepository;
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasSettingAccess(Authentication authentication, String permissionCode, UUID settingId) {
        if (settingId == null) {
            return false;
        }
        return settingRepository.findWithDetailsBySettingId(settingId)
                .map(setting -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "WAREHOUSE",
                        setting.getWarehouse().getWarehouseId()))
                .orElse(false);
    }
}
