package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The record guard behind every {@code /customers/{customerId}} endpoint: a customer is company-owned,
 * so access is decided on its <em>company</em>, never on the raw customer id.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SalesPermissionGuard.hasCustomerAccess")
class SalesPermissionGuardTest {

    @Mock PermissionGuard permissionGuard;
    @Mock SalesOrderRepository salesOrderRepository;
    @Mock CustomerRepository customerRepository;

    SalesPermissionGuard guard;
    final Authentication authentication = new UsernamePasswordAuthenticationToken("user", null, List.of());

    @BeforeEach
    void setUp() {
        guard = new SalesPermissionGuard(permissionGuard, salesOrderRepository, customerRepository);
    }

    @Test
    void hasCustomerAccess_resolvesTheCustomerToItsCompanyScope() {
        Customer customer = customer();
        UUID companyId = customer.getCompany().getCompanyId();
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(permissionGuard.hasResourceAccess(authentication, "PERM_CUSTOMER_READ", "COMPANY", companyId))
                .thenReturn(true);

        assertThat(guard.hasCustomerAccess(authentication, "PERM_CUSTOMER_READ", customer.getCustomerId())).isTrue();
        verify(permissionGuard).hasResourceAccess(authentication, "PERM_CUSTOMER_READ", "COMPANY", companyId);
    }

    @Test
    void hasCustomerAccess_deniedWhenTheCompanyScopeIsMissing() {
        Customer customer = customer();
        UUID companyId = customer.getCompany().getCompanyId();
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(permissionGuard.hasResourceAccess(authentication, "PERM_CUSTOMER_MANAGE", "COMPANY", companyId))
                .thenReturn(false);

        assertThat(guard.hasCustomerAccess(authentication, "PERM_CUSTOMER_MANAGE", customer.getCustomerId())).isFalse();
    }

    @Test
    void hasCustomerAccess_unknownCustomerIsDeniedWithoutConsultingPermissions() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findWithCompanyByCustomerId(customerId)).thenReturn(Optional.empty());

        assertThat(guard.hasCustomerAccess(authentication, "PERM_CUSTOMER_READ", customerId)).isFalse();
        verifyNoInteractions(permissionGuard);
    }

    @Test
    void hasCustomerAccess_nullIdIsDenied() {
        assertThat(guard.hasCustomerAccess(authentication, "PERM_CUSTOMER_READ", null)).isFalse();
        verifyNoInteractions(customerRepository, permissionGuard);
    }

    private static Customer customer() {
        Company company = Company.builder().companyId(UUID.randomUUID()).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
        return Customer.builder().customerId(UUID.randomUUID()).company(company).code("CUS-001")
                .name("Acme Retail").build();
    }
}
