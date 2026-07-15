package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("planningPermissionGuard")
@RequiredArgsConstructor
public class PlanningPermissionGuard {

    private final PermissionGuard permissionGuard;

    public boolean canEstimate(Authentication authentication, ProductionEstimateRequest request) {
        if (request == null || request.scopeType() == null || request.scopeId() == null) {
            return false;
        }
        return permissionGuard.hasResourceAccess(
                authentication,
                "PERM_PLANNING_READ",
                request.scopeType().name(),
                request.scopeId());
    }
}
