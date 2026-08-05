package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.shift.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Guards the aggregate-identified endpoints ({@code /shifts/{shiftId}/...}), which carry no
 * {@code plantId} of their own to authorize against directly — same shape as
 * {@code WorkCenterPermissionGuard}.
 */
@Component("shiftPermissionGuard")
@RequiredArgsConstructor
public class ShiftPermissionGuard {

    private final ShiftRepository shiftRepository;
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasShiftAccess(Authentication authentication, String permissionCode, UUID shiftId) {
        if (shiftId == null) {
            return false;
        }
        return shiftRepository.findById(shiftId)
                .map(shift -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "PLANT",
                        shift.getPlant().getPlantId()))
                .orElse(false);
    }
}
