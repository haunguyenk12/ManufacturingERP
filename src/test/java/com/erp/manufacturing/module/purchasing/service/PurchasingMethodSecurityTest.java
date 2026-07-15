package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.dto.SupplierCreateRequest;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.ItemSupplierRepository;
import com.erp.manufacturing.module.purchasing.repository.SupplierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PurchasingMethodSecurityTest.Config.class)
@DisplayName("Purchasing method security")
class PurchasingMethodSecurityTest {

    @Autowired SupplierService supplierService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired SupplierRepository supplierRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, supplierRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSupplier_deniedWhenMissingPermission() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_SUPPLIER_MANAGE"), eq("COMPANY"), eq(companyId)))
                .thenReturn(false);

        assertThatThrownBy(() -> supplierService.create(new SupplierCreateRequest(
                companyId, "SUP", "Supplier", null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(supplierRepository);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        SupplierService supplierService(SupplierRepository supplierRepository,
                                        ItemSupplierRepository itemSupplierRepository,
                                        OrganizationLookupService organizationLookupService,
                                        ItemLookupService itemLookupService,
                                        PurchasingMapper mapper) {
            return new SupplierService(
                    supplierRepository,
                    itemSupplierRepository,
                    organizationLookupService,
                    itemLookupService,
                    mapper);
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean
        SupplierRepository supplierRepository() {
            return mock(SupplierRepository.class);
        }

        @Bean
        ItemSupplierRepository itemSupplierRepository() {
            return mock(ItemSupplierRepository.class);
        }

        @Bean
        OrganizationLookupService organizationLookupService() {
            return mock(OrganizationLookupService.class);
        }

        @Bean
        ItemLookupService itemLookupService() {
            return mock(ItemLookupService.class);
        }

        @Bean
        PurchasingMapper purchasingMapper() {
            return new PurchasingMapper();
        }
    }
}
