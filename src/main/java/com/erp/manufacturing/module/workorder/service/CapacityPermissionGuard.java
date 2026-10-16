package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Authorisation of the capacity preview (G5, FE §1.4): a preview reads the routing, BOM, work centers,
 * calendars and shifts, so the caller must be allowed to read every one of them — capacity, work
 * center, calendar and shift on the plant; routing and BOM on the plant's company (both are
 * company master data, D5). Holding only the capacity permission must not reveal the rest.
 */
@Component("capacityPermissionGuard")
@RequiredArgsConstructor
public class CapacityPermissionGuard {

    private final PermissionGuard permissionGuard;
    private final OrganizationLookupService organizationLookupService;

    @Transactional(readOnly = true)
    public boolean canPreview(Authentication authentication, UUID plantId) {
        if (plantId == null) {
            return false;
        }
        UUID companyId;
        try {
            companyId = organizationLookupService.getPlant(plantId).getCompany().getCompanyId();
        } catch (RuntimeException missingPlant) {
            return false;
        }
        return plant(authentication, "PERM_CAPACITY_READ", plantId)
                && plant(authentication, "PERM_WORK_CENTER_READ", plantId)
                && plant(authentication, "PERM_WORK_CALENDAR_READ", plantId)
                && plant(authentication, "PERM_SHIFT_READ", plantId)
                && permissionGuard.hasResourceAccess(authentication, "PERM_ROUTING_READ", "COMPANY", companyId)
                && permissionGuard.hasResourceAccess(authentication, "PERM_BOM_READ", "COMPANY", companyId);
    }

    private boolean plant(Authentication authentication, String permission, UUID plantId) {
        return permissionGuard.hasResourceAccess(authentication, permission, "PLANT", plantId);
    }
}
