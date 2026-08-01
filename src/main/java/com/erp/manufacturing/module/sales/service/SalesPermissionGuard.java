package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("salesPermissionGuard")
@RequiredArgsConstructor
public class SalesPermissionGuard {

    private final PermissionGuard permissionGuard;
    private final SalesOrderRepository salesOrderRepository;

    @Transactional(readOnly = true)
    public boolean hasOrderAccess(Authentication authentication, String permissionCode, UUID salesOrderId) {
        if (salesOrderId == null) {
            return false;
        }
        return salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)
                .map(order -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "PLANT", order.getPlant().getPlantId()))
                .orElse(false);
    }
}
