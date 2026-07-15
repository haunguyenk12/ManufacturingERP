package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.domain.InventoryAlertStatus;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
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

@SpringJUnitConfig(InventoryAlertMethodSecurityTest.Config.class)
@DisplayName("InventoryAlertService method security")
class InventoryAlertMethodSecurityTest {

    @Autowired InventoryAlertService alertService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired OrganizationLookupService organizationLookupService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, organizationLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listAlerts_deniedWhenReadScopeMissing() {
        UUID warehouseId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("WAREHOUSE"), eq(warehouseId)))
                .thenReturn(false);

        assertThatThrownBy(() -> alertService.listAlerts(
                        ScopeResourceType.WAREHOUSE, warehouseId, InventoryAlertStatus.LOW_STOCK))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(organizationLookupService);
    }

    @Test
    void getDashboard_deniedWhenReadScopeMissing() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("COMPANY"), eq(companyId)))
                .thenReturn(false);

        assertThatThrownBy(() -> alertService.getDashboard(ScopeResourceType.COMPANY, companyId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(organizationLookupService);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        InventoryAlertService inventoryAlertService(ItemWarehouseSettingRepository settingRepository,
                                                    InventoryAvailabilityService availabilityService,
                                                    OrganizationLookupService organizationLookupService,
                                                    StockMovementRepository stockMovementRepository,
                                                    InventoryMapper mapper) {
            return new InventoryAlertService(
                    settingRepository,
                    availabilityService,
                    organizationLookupService,
                    stockMovementRepository,
                    mapper);
        }

        @Bean
        InventoryMapper inventoryMapper() {
            return new InventoryMapper();
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean
        ItemWarehouseSettingRepository itemWarehouseSettingRepository() {
            return mock(ItemWarehouseSettingRepository.class);
        }

        @Bean
        InventoryAvailabilityService inventoryAvailabilityService() {
            return mock(InventoryAvailabilityService.class);
        }

        @Bean
        OrganizationLookupService organizationLookupService() {
            return mock(OrganizationLookupService.class);
        }

        @Bean
        StockMovementRepository stockMovementRepository() {
            return mock(StockMovementRepository.class);
        }
    }
}
