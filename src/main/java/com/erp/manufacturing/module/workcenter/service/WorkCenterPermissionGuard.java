package com.erp.manufacturing.module.workcenter.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.workcenter.repository.WorkCenterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Guards the aggregate-identified endpoints ({@code /work-centers/{workCenterId}/...}), which carry
 * no {@code plantId} of their own to authorize against directly — same shape as
 * {@code RoutingPermissionGuard}.
 */
@Component("workCenterPermissionGuard")
@RequiredArgsConstructor
public class WorkCenterPermissionGuard {

    private final WorkCenterRepository workCenterRepository;
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasWorkCenterAccess(Authentication authentication, String permissionCode, UUID workCenterId) {
        if (workCenterId == null) {
            return false;
        }
        return workCenterRepository.findById(workCenterId)
                .map(workCenter -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "PLANT",
                        workCenter.getPlant().getPlantId()))
                .orElse(false);
    }
}
