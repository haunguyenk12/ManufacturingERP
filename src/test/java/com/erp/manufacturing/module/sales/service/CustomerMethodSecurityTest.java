package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.dto.CustomerCreateRequest;
import com.erp.manufacturing.module.sales.dto.CustomerUpdateRequest;
import com.erp.manufacturing.module.sales.mapper.CustomerMapper;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Locks the exact {@code PERM_CUSTOMER_*} string and scope of every {@link CustomerService} method.
 * Each deny case {@code verify}s the guard was asked with that string: a typo in {@code @PreAuthorize}
 * would make an unmatched stub return the mock default {@code false} and the deny case would still be
 * green without it (rule R2).
 */
@SpringJUnitConfig(CustomerMethodSecurityTest.Config.class)
@DisplayName("CustomerService method security")
class CustomerMethodSecurityTest {

    @Autowired CustomerService customerService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired SalesPermissionGuard salesPermissionGuard;
    @Autowired CustomerRepository customerRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, salesPermissionGuard, customerRepository, organizationLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_deniedWhenCustomerManageMissingOnTheCompany() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq("COMPANY"), eq(companyId)))
                .thenReturn(false);

        assertThatThrownBy(() -> customerService.create(companyId, createRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(customerRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq("COMPANY"), eq(companyId));
    }

    @Test
    void create_allowedWhenCustomerManagePresentOnTheCompany() {
        Company company = company();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq("COMPANY"),
                eq(company.getCompanyId()))).thenReturn(true);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> customerService.create(company.getCompanyId(), createRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void list_deniedWhenCustomerReadMissingOnTheCompany() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_CUSTOMER_READ"), eq("COMPANY"), eq(companyId)))
                .thenReturn(false);

        assertThatThrownBy(() -> customerService.list(companyId, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(customerRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_CUSTOMER_READ"), eq("COMPANY"), eq(companyId));
    }

    @Test
    void list_allowedWhenCustomerReadPresentOnTheCompany() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_CUSTOMER_READ"), eq("COMPANY"), eq(companyId)))
                .thenReturn(true);
        when(organizationLookupService.companyExists(companyId)).thenReturn(true);
        when(customerRepository.search(eq(companyId), isNull(), isNull(), any())).thenReturn(Page.empty());

        assertThatCode(() -> customerService.list(companyId, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void get_deniedWhenCustomerReadMissingOnTheRecord() {
        UUID customerId = UUID.randomUUID();
        when(salesPermissionGuard.hasCustomerAccess(any(), eq("PERM_CUSTOMER_READ"), eq(customerId)))
                .thenReturn(false);

        assertThatThrownBy(() -> customerService.get(customerId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(customerRepository);
        verify(salesPermissionGuard).hasCustomerAccess(any(), eq("PERM_CUSTOMER_READ"), eq(customerId));
    }

    @Test
    void get_allowedWhenCustomerReadPresentOnTheRecord() {
        Customer customer = customer();
        when(salesPermissionGuard.hasCustomerAccess(any(), eq("PERM_CUSTOMER_READ"), eq(customer.getCustomerId())))
                .thenReturn(true);
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));

        assertThatCode(() -> customerService.get(customer.getCustomerId())).doesNotThrowAnyException();
    }

    @Test
    void update_deniedWhenCustomerManageMissingOnTheRecord() {
        UUID customerId = UUID.randomUUID();
        when(salesPermissionGuard.hasCustomerAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq(customerId)))
                .thenReturn(false);

        assertThatThrownBy(() -> customerService.update(customerId,
                new CustomerUpdateRequest("New name", null, null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(customerRepository);
        verify(salesPermissionGuard).hasCustomerAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq(customerId));
    }

    @Test
    void activate_deniedWhenCustomerManageMissingOnTheRecord() {
        UUID customerId = UUID.randomUUID();
        when(salesPermissionGuard.hasCustomerAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq(customerId)))
                .thenReturn(false);

        assertThatThrownBy(() -> customerService.activate(customerId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(customerRepository);
        verify(salesPermissionGuard).hasCustomerAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq(customerId));
    }

    @Test
    void deactivate_deniedWhenCustomerManageMissingOnTheRecord() {
        UUID customerId = UUID.randomUUID();
        when(salesPermissionGuard.hasCustomerAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq(customerId)))
                .thenReturn(false);

        assertThatThrownBy(() -> customerService.deactivate(customerId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(customerRepository);
        verify(salesPermissionGuard).hasCustomerAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq(customerId));
    }

    @Test
    void deactivate_allowedWhenCustomerManagePresentOnTheRecord() {
        Customer customer = customer();
        when(salesPermissionGuard.hasCustomerAccess(any(), eq("PERM_CUSTOMER_MANAGE"), eq(customer.getCustomerId())))
                .thenReturn(true);
        when(customerRepository.findWithCompanyByCustomerId(customer.getCustomerId())).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        assertThatCode(() -> customerService.deactivate(customer.getCustomerId())).doesNotThrowAnyException();
    }

    private static CustomerCreateRequest createRequest() {
        return new CustomerCreateRequest("CUS-001", "Acme Retail", null, null, null, null, null);
    }

    private static Company company() {
        return Company.builder().companyId(UUID.randomUUID()).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
    }

    private static Customer customer() {
        return Customer.builder().customerId(UUID.randomUUID()).company(company()).code("CUS-001")
                .name("Acme Retail").status(CustomerStatus.ACTIVE).build();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        CustomerService customerService(CustomerRepository customerRepository,
                                        OrganizationLookupService organizationLookupService) {
            return new CustomerService(customerRepository, organizationLookupService,
                    new CustomerMapper());
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "salesPermissionGuard")
        SalesPermissionGuard salesPermissionGuard() { return mock(SalesPermissionGuard.class); }

        @Bean CustomerRepository customerRepository() { return mock(CustomerRepository.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
    }
}
