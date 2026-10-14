package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
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
    private final CustomerRepository customerRepository;

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

    /** Customer is company-owned master data, so a {@code {customerId}} path resolves to its company. */
    @Transactional(readOnly = true)
    public boolean hasCustomerAccess(Authentication authentication, String permissionCode, UUID customerId) {
        if (customerId == null) {
            return false;
        }
        return customerRepository.findWithCompanyByCustomerId(customerId)
                .map(customer -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "COMPANY", customer.getCompany().getCompanyId()))
                .orElse(false);
    }
}
