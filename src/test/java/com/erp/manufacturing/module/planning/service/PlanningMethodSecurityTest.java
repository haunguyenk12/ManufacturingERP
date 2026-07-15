package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateRequest;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PlanningMethodSecurityTest.Config.class)
@DisplayName("PlanningService method security")
class PlanningMethodSecurityTest {

    @Autowired PlanningService planningService;
    @Autowired PlanningPermissionGuard planningPermissionGuard;
    @Autowired ItemLookupService itemLookupService;

    @BeforeEach
    void setUp() {
        reset(planningPermissionGuard, itemLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void estimateProduction_deniedWhenPlanningGuardDenies() {
        ProductionEstimateRequest request = new ProductionEstimateRequest(
                UUID.randomUUID(), ScopeResourceType.WAREHOUSE, UUID.randomUUID(), BigDecimal.ONE);
        when(planningPermissionGuard.canEstimate(any(), eq(request))).thenReturn(false);

        assertThatThrownBy(() -> planningService.estimateProduction(request))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(itemLookupService);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        PlanningService planningService(ItemLookupService itemLookupService,
                                        BomLookupService bomLookupService,
                                        InventoryAvailabilityService inventoryAvailabilityService,
                                        OrganizationLookupService organizationLookupService) {
            return new PlanningService(
                    itemLookupService,
                    bomLookupService,
                    inventoryAvailabilityService,
                    organizationLookupService);
        }

        @Bean(name = "planningPermissionGuard")
        PlanningPermissionGuard planningPermissionGuard() {
            return mock(PlanningPermissionGuard.class);
        }

        @Bean
        ItemLookupService itemLookupService() {
            return mock(ItemLookupService.class);
        }

        @Bean
        BomLookupService bomLookupService() {
            return mock(BomLookupService.class);
        }

        @Bean
        InventoryAvailabilityService inventoryAvailabilityService() {
            return mock(InventoryAvailabilityService.class);
        }

        @Bean
        OrganizationLookupService organizationLookupService() {
            return mock(OrganizationLookupService.class);
        }
    }
}
