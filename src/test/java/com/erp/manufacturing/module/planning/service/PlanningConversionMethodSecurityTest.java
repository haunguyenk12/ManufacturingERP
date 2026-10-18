package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.dto.CommercialPlanningRunRequest;
import com.erp.manufacturing.module.planning.dto.PlanningConversionEntryRequest;
import com.erp.manufacturing.module.planning.dto.PlanningConversionRequest;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.*;
import com.erp.manufacturing.module.purchasing.service.PurchaseOrderService;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSourceService;
import com.erp.manufacturing.module.workorder.service.WorkOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * G6b permissions: a commercial run needs {@code PERM_MRP_RUN} on the plant; converting needs
 * {@code PERM_SUPPLY_SUGGESTION_MANAGE} on the run's plant (FE Q5 — no purchase order permission
 * until GĐ7); reading conversions and one requirement needs {@code PERM_MRP_READ}.
 */
@SpringJUnitConfig(PlanningConversionMethodSecurityTest.Config.class)
@DisplayName("Planning conversion method security (G6b)")
class PlanningConversionMethodSecurityTest {

    @Autowired PlanningConversionService conversionService;
    @Autowired MrpRunService mrpRunService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired MrpPlanningPermissionGuard mrpPlanningPermissionGuard;
    @Autowired MrpRunRepository mrpRunRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, mrpPlanningPermissionGuard, mrpRunRepository, organizationLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runCommercial_deniedWithoutMrpRunOnThePlant() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_MRP_RUN"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> mrpRunService.runCommercial(new CommercialPlanningRunRequest(
                UUID.randomUUID(), plantId, LocalDate.now(), LocalDate.now(), List.of(UUID.randomUUID()), null, false), null))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_MRP_RUN"), eq("PLANT"), eq(plantId));
        verifyNoInteractions(organizationLookupService, mrpRunRepository);
    }

    @Test
    void convert_deniedWithoutSupplySuggestionManageOnTheRun() {
        UUID runId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasRunAccess(any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq(runId))).thenReturn(false);

        assertThatThrownBy(() -> conversionService.convert(runId, request(), null))
                .isInstanceOf(AccessDeniedException.class);

        verify(mrpPlanningPermissionGuard).hasRunAccess(any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq(runId));
        verifyNoInteractions(mrpRunRepository);
    }

    @Test
    void convert_allowedWithSupplySuggestionManage_reachesTheRunLock() {
        UUID runId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasRunAccess(any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq(runId))).thenReturn(true);
        when(mrpRunRepository.lockByMrpRunId(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.convert(runId, request(), null))
                .isInstanceOf(AppException.class);

        verify(mrpRunRepository).lockByMrpRunId(runId);
    }

    @Test
    void listConversions_deniedWithoutMrpRead() {
        UUID runId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasRunAccess(any(), eq("PERM_MRP_READ"), eq(runId))).thenReturn(false);

        assertThatThrownBy(() -> conversionService.list(runId, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(mrpPlanningPermissionGuard).hasRunAccess(any(), eq("PERM_MRP_READ"), eq(runId));
        verifyNoInteractions(mrpRunRepository);
    }

    @Test
    void getRequirement_deniedWithoutMrpRead() {
        UUID runId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasRunAccess(any(), eq("PERM_MRP_READ"), eq(runId))).thenReturn(false);

        assertThatThrownBy(() -> mrpRunService.getRequirement(runId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verify(mrpPlanningPermissionGuard).hasRunAccess(any(), eq("PERM_MRP_READ"), eq(runId));
    }

    private PlanningConversionRequest request() {
        return new PlanningConversionRequest("v1", 0,
                List.of(new PlanningConversionEntryRequest(UUID.randomUUID(), BigDecimal.ONE)), null);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        PlanningConversionService planningConversionService(MrpRunRepository mrpRunRepository, MrpPlanningMapper mapper) {
            return new PlanningConversionService(mrpRunRepository, mock(MrpRequirementLineRepository.class),
                    mock(SupplySuggestionRepository.class), mock(SupplySuggestionConversionRepository.class),
                    mock(PlanningSourceVersionCalculator.class), mock(SalesOrderPlanningSourceService.class),
                    mock(WorkOrderService.class), mock(PurchaseOrderService.class), mapper,
                    new IdempotencySupport(new ObjectMapper().findAndRegisterModules()));
        }

        @Bean
        MrpRunService mrpRunService(MrpRunRepository mrpRunRepository,
                                    OrganizationLookupService organizationLookupService,
                                    MrpPlanningMapper mapper) {
            return new MrpRunService(mrpRunRepository, mock(PlanningDemandRepository.class),
                    mock(MrpRunDemandRepository.class), mock(MrpRequirementLineRepository.class),
                    mock(SupplySuggestionRepository.class), organizationLookupService, mock(MrpCalculationService.class),
                    mapper, mock(AuditLogService.class), new IdempotencySupport(new ObjectMapper().findAndRegisterModules()),
                    mock(MrpRunStateRecorder.class), mock(SalesOrderPlanningSourceService.class),
                    mock(PlanningSourceVersionCalculator.class), mock(SupplySuggestionConversionRepository.class),
                    mock(ItemLookupService.class));
        }

        @Bean MrpPlanningMapper mrpPlanningMapper() { return new MrpPlanningMapper(); }

        @Bean MrpRunRepository mrpRunRepository() { return mock(MrpRunRepository.class); }

        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "mrpPlanningPermissionGuard")
        MrpPlanningPermissionGuard mrpPlanningPermissionGuard() { return mock(MrpPlanningPermissionGuard.class); }
    }
}
