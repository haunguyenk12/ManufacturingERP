package com.erp.manufacturing.module.routing.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("routingPermissionGuard")
@RequiredArgsConstructor
public class RoutingPermissionGuard {

    private final RoutingHeaderRepository routingHeaderRepository;
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasRoutingAccess(Authentication authentication, String permissionCode, UUID routingId) {
        if (routingId == null) {
            return false;
        }
        return routingHeaderRepository.findById(routingId)
                .map(routing -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "COMPANY",
                        routing.getCompany().getCompanyId()))
                .orElse(false);
    }
}
