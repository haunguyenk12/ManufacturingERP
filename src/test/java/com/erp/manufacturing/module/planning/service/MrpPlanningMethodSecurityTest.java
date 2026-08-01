package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.dto.MrpRunCreateRequest;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.*;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(MrpPlanningMethodSecurityTest.Config.class)
@DisplayName("MRP planning method security")
class MrpPlanningMethodSecurityTest {

    @Autowired MrpRunService mrpRunService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired MrpRunRepository mrpRunRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, mrpRunRepository, organizationLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void run_deniedWhenMissingMrpRunPermission() {
        UUID plantId = UUID.randomUUID();
        MrpRunCreateRequest request = new MrpRunCreateRequest(
                UUID.randomUUID(),
                plantId,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                null);
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_MRP_RUN"), eq("PLANT"), eq(plantId)))
                .thenReturn(false);

        assertThatThrownBy(() -> mrpRunService.run(request))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(mrpRunRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_MRP_RUN"), eq("PLANT"), eq(plantId));
    }

    @Test
    void list_allowedWhenMrpReadScopePresent() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        Company company = Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_MRP_READ"), eq("PLANT"), eq(plantId)))
                .thenReturn(true);
        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(Plant.builder()
                .plantId(plantId)
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build());
        when(mrpRunRepository.search(eq(companyId), eq(plantId), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> mrpRunService.list(companyId, plantId, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        MrpRunService mrpRunService(MrpRunRepository mrpRunRepository,
                                    PlanningDemandRepository planningDemandRepository,
                                    MrpRunDemandRepository mrpRunDemandRepository,
                                    MrpRequirementLineRepository requirementLineRepository,
                                    SupplySuggestionRepository supplySuggestionRepository,
                                    OrganizationLookupService organizationLookupService,
                                    MrpCalculationService calculationService,
                                    MrpPlanningMapper mapper,
                                    AuditLogService auditLogService) {
            return new MrpRunService(
                    mrpRunRepository,
                    planningDemandRepository,
                    mrpRunDemandRepository,
                    requirementLineRepository,
                    supplySuggestionRepository,
                    organizationLookupService,
                    calculationService,
                    mapper,
                    auditLogService);
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean
        MrpPlanningMapper mrpPlanningMapper() {
            return new MrpPlanningMapper();
        }

        @Bean
        MrpRunRepository mrpRunRepository() {
            return mock(MrpRunRepository.class);
        }

        @Bean
        PlanningDemandRepository planningDemandRepository() {
            return mock(PlanningDemandRepository.class);
        }

        @Bean
        MrpRunDemandRepository mrpRunDemandRepository() {
            return mock(MrpRunDemandRepository.class);
        }

        @Bean
        MrpRequirementLineRepository mrpRequirementLineRepository() {
            return mock(MrpRequirementLineRepository.class);
        }

        @Bean
        SupplySuggestionRepository supplySuggestionRepository() {
            return mock(SupplySuggestionRepository.class);
        }

        @Bean
        OrganizationLookupService organizationLookupService() {
            return mock(OrganizationLookupService.class);
        }

        @Bean
        MrpCalculationService mrpCalculationService() {
            return mock(MrpCalculationService.class);
        }

        @Bean
        AuditLogService auditLogService() {
            return mock(AuditLogService.class);
        }
    }
}
