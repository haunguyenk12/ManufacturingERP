package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.shift.repository.WorkCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Guards the aggregate-identified endpoints ({@code /work-calendars/{workCalendarId}/...}) — same
 * shape as {@code ShiftPermissionGuard}/{@code WorkCenterPermissionGuard}.
 */
@Component("workCalendarPermissionGuard")
@RequiredArgsConstructor
public class WorkCalendarPermissionGuard {

    private final WorkCalendarRepository workCalendarRepository;
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasWorkCalendarAccess(Authentication authentication, String permissionCode, UUID workCalendarId) {
        if (workCalendarId == null) {
            return false;
        }
        return workCalendarRepository.findById(workCalendarId)
                .map(calendar -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "PLANT",
                        calendar.getPlant().getPlantId()))
                .orElse(false);
    }
}
