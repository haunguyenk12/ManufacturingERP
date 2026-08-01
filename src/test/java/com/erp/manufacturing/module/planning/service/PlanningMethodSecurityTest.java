package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PlanningMethodSecurityTest.Config.class)
@DisplayName("PlanningService method security")
class PlanningMethodSecurityTest {

    @Autowired PlanningService planningService;
    @Autowired PlanningPermissionGuard planningPermissionGuard;
    @Autowired ItemLookupService itemLookupService;
    @Autowired BomLookupService bomLookupService;
    @Autowired InventoryAvailabilityService inventoryAvailabilityService;
    @Autowired OrganizationLookupService organizationLookupService;

    @BeforeEach
    void setUp() {
        reset(planningPermissionGuard, itemLookupService, bomLookupService,
                inventoryAvailabilityService, organizationLookupService);
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
        verify(planningPermissionGuard).canEstimate(any(), eq(request));
    }

    @Test
    void estimateProduction_allowedWhenPlanningGuardAllows() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID productItemId = UUID.randomUUID();
        ProductionEstimateRequest request = new ProductionEstimateRequest(
                productItemId, ScopeResourceType.WAREHOUSE, warehouseId, BigDecimal.ONE);
        Company company = Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Item product = Item.builder()
                .itemId(productItemId)
                .company(company)
                .code("FG-100")
                .name("Finished Good")
                .type(ItemType.FINISHED_GOOD)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();

        when(planningPermissionGuard.canEstimate(any(), eq(request))).thenReturn(true);
        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(new OrganizationScopeResolution(
                        ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(itemLookupService.getActiveItem(productItemId)).thenReturn(product);
        when(bomLookupService.getActiveBom(companyId, productItemId)).thenReturn(BomHeader.builder()
                .bomId(UUID.randomUUID())
                .company(company)
                .parentItem(product)
                .revision("R1")
                .status(BomStatus.ACTIVE)
                .lines(new ArrayList<>())
                .build());
        when(inventoryAvailabilityService.getAvailableQuantities(anySet(), anyList())).thenReturn(Map.of());

        assertThatCode(() -> planningService.estimateProduction(request)).doesNotThrowAnyException();
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
