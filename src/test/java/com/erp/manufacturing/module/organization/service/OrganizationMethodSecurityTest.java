package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.mapper.OrganizationMapper;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(OrganizationMethodSecurityTest.Config.class)
@DisplayName("OrganizationService method security")
class OrganizationMethodSecurityTest {

    @Autowired OrganizationService organizationService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired CompanyRepository companyRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, companyRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCompany_deniedWhenGuardDenies() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_ORG_READ"), eq("COMPANY"), eq(companyId)))
                .thenReturn(false);

        assertThatThrownBy(() -> organizationService.getCompany(companyId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(companyRepository);
    }

    @Test
    void getCompany_allowedWhenGuardAllows() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_ORG_READ"), eq("COMPANY"), eq(companyId)))
                .thenReturn(true);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build()));

        assertThat(organizationService.getCompany(companyId).companyId()).isEqualTo(companyId);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        OrganizationService organizationService(CompanyRepository companyRepository,
                                                PlantRepository plantRepository,
                                                WarehouseRepository warehouseRepository,
                                                OrganizationMapper mapper) {
            return new OrganizationService(companyRepository, plantRepository, warehouseRepository, mapper);
        }

        @Bean
        OrganizationMapper organizationMapper() {
            return new OrganizationMapper();
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean
        CompanyRepository companyRepository() {
            return mock(CompanyRepository.class);
        }

        @Bean
        PlantRepository plantRepository() {
            return mock(PlantRepository.class);
        }

        @Bean
        WarehouseRepository warehouseRepository() {
            return mock(WarehouseRepository.class);
        }
    }
}
