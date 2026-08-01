package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.PlanningDemandRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PlanningDemandMethodSecurityTest.Config.class)
@DisplayName("PlanningDemandService method security")
class PlanningDemandMethodSecurityTest {

    @Autowired PlanningDemandService planningDemandService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired MrpPlanningPermissionGuard mrpPlanningPermissionGuard;
    @Autowired PlanningDemandRepository planningDemandRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, mrpPlanningPermissionGuard, planningDemandRepository, organizationLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_deniedWhenDemandReadMissing() {
        UUID demandId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasDemandAccess(any(), eq("PERM_PLANNING_DEMAND_READ"), eq(demandId)))
                .thenReturn(false);

        assertThatThrownBy(() -> planningDemandService.get(demandId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(planningDemandRepository);
        verify(mrpPlanningPermissionGuard).hasDemandAccess(any(), eq("PERM_PLANNING_DEMAND_READ"), eq(demandId));
    }

    @Test
    void cancel_deniedWhenDemandManageMissing() {
        UUID demandId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasDemandAccess(any(), eq("PERM_PLANNING_DEMAND_MANAGE"), eq(demandId)))
                .thenReturn(false);

        assertThatThrownBy(() -> planningDemandService.cancel(demandId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(planningDemandRepository);
        verify(mrpPlanningPermissionGuard).hasDemandAccess(any(), eq("PERM_PLANNING_DEMAND_MANAGE"), eq(demandId));
    }

    @Test
    void list_deniedWhenPlantDemandReadScopeMissing() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_PLANNING_DEMAND_READ"), eq("PLANT"), eq(plantId)))
                .thenReturn(false);

        assertThatThrownBy(() -> planningDemandService.list(
                companyId, plantId, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(planningDemandRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_PLANNING_DEMAND_READ"), eq("PLANT"), eq(plantId));
    }

    @Test
    void list_allowedWhenPlantDemandReadScopePresent() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        Company company = Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_PLANNING_DEMAND_READ"), eq("PLANT"), eq(plantId)))
                .thenReturn(true);
        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(Plant.builder()
                .plantId(plantId)
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build());
        when(planningDemandRepository.search(eq(companyId), eq(plantId), isNull(), isNull(), isNull(),
                any(Pageable.class))).thenReturn(Page.empty());

        assertThatCode(() -> planningDemandService.list(
                companyId, plantId, null, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        PlanningDemandService planningDemandService(PlanningDemandRepository planningDemandRepository,
                                                    OrganizationLookupService organizationLookupService,
                                                    ItemLookupService itemLookupService,
                                                    MrpPlanningMapper mapper) {
            return new PlanningDemandService(
                    planningDemandRepository, organizationLookupService, itemLookupService, mapper);
        }

        @Bean MrpPlanningMapper mrpPlanningMapper() { return new MrpPlanningMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "mrpPlanningPermissionGuard")
        MrpPlanningPermissionGuard mrpPlanningPermissionGuard() { return mock(MrpPlanningPermissionGuard.class); }

        @Bean PlanningDemandRepository planningDemandRepository() { return mock(PlanningDemandRepository.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
        @Bean ItemLookupService itemLookupService() { return mock(ItemLookupService.class); }
    }
}
