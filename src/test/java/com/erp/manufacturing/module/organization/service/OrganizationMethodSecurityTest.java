package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
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
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, companyRepository, plantRepository, warehouseRepository);
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
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_ORG_READ"), eq("COMPANY"), eq(companyId));
    }

    @Test
    void deactivateCompany_deniedWhenOrgManageScopeMissing() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_ORG_MANAGE"), eq("COMPANY"), eq(companyId)))
                .thenReturn(false);

        assertThatThrownBy(() -> organizationService.deactivateCompany(companyId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(companyRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_ORG_MANAGE"), eq("COMPANY"), eq(companyId));
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

    @Test
    void activatePlant_deniedWhenOrgManageScopeMissing() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_ORG_MANAGE"), eq("PLANT"), eq(plantId)))
                .thenReturn(false);

        assertThatThrownBy(() -> organizationService.activatePlant(plantId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(plantRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_ORG_MANAGE"), eq("PLANT"), eq(plantId));
    }

    @Test
    void activatePlant_allowedWhenGuardAllows() {
        UUID plantId = UUID.randomUUID();
        Company company = Company.builder()
                .companyId(UUID.randomUUID())
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Plant plant = Plant.builder()
                .plantId(plantId)
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.INACTIVE)
                .build();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_ORG_MANAGE"), eq("PLANT"), eq(plantId)))
                .thenReturn(true);
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));
        when(plantRepository.save(plant)).thenReturn(plant);

        assertThat(organizationService.activatePlant(plantId).status()).isEqualTo("ACTIVE");
    }

    @Test
    void activateWarehouse_deniedWhenOrgManageScopeMissing() {
        UUID warehouseId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_ORG_MANAGE"), eq("WAREHOUSE"), eq(warehouseId)))
                .thenReturn(false);

        assertThatThrownBy(() -> organizationService.activateWarehouse(warehouseId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(warehouseRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_ORG_MANAGE"), eq("WAREHOUSE"), eq(warehouseId));
    }

    @Test
    void activateWarehouse_allowedWhenGuardAllows() {
        UUID warehouseId = UUID.randomUUID();
        Company company = Company.builder()
                .companyId(UUID.randomUUID())
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Plant plant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Warehouse warehouse = Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant)
                .code("RM")
                .name("Raw")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.INACTIVE)
                .build();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_ORG_MANAGE"), eq("WAREHOUSE"), eq(warehouseId)))
                .thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(warehouse)).thenReturn(warehouse);

        assertThat(organizationService.activateWarehouse(warehouseId).status()).isEqualTo("ACTIVE");
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
