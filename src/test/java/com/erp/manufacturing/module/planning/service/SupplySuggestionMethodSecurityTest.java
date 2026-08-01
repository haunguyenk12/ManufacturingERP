package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderService;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(SupplySuggestionMethodSecurityTest.Config.class)
@DisplayName("SupplySuggestionService method security")
class SupplySuggestionMethodSecurityTest {

    @Autowired SupplySuggestionService supplySuggestionService;
    @Autowired MrpPlanningPermissionGuard mrpPlanningPermissionGuard;
    @Autowired SupplySuggestionRepository supplySuggestionRepository;
    @Autowired WorkOrderService workOrderService;

    @BeforeEach
    void setUp() {
        reset(mrpPlanningPermissionGuard, supplySuggestionRepository, workOrderService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approve_deniedWhenSuggestionManageMissing() {
        UUID suggestionId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasSuggestionAccess(
                any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq(suggestionId))).thenReturn(false);

        assertThatThrownBy(() -> supplySuggestionService.approve(suggestionId, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(supplySuggestionRepository, workOrderService);
        verify(mrpPlanningPermissionGuard).hasSuggestionAccess(
                any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq(suggestionId));
    }

    @Test
    void reject_deniedWhenSuggestionManageMissing() {
        UUID suggestionId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasSuggestionAccess(
                any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq(suggestionId))).thenReturn(false);

        assertThatThrownBy(() -> supplySuggestionService.reject(suggestionId, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(supplySuggestionRepository, workOrderService);
        verify(mrpPlanningPermissionGuard).hasSuggestionAccess(
                any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq(suggestionId));
    }

    @Test
    void approve_allowedWhenSuggestionManagePresent() {
        UUID suggestionId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasSuggestionAccess(
                any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq(suggestionId))).thenReturn(true);
        when(supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestionId))
                .thenReturn(Optional.empty());

        // Guard passes, so execution reaches the service body: it must fail on the missing
        // suggestion, NOT on authorization.
        assertThatThrownBy(() -> supplySuggestionService.approve(suggestionId, null))
                .isNotInstanceOf(AccessDeniedException.class);

        verify(supplySuggestionRepository).findWithDetailsBySupplySuggestionId(suggestionId);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        SupplySuggestionService supplySuggestionService(SupplySuggestionRepository supplySuggestionRepository,
                                                        WorkOrderService workOrderService,
                                                        MrpPlanningMapper mapper) {
            return new SupplySuggestionService(supplySuggestionRepository, workOrderService, mapper);
        }

        @Bean MrpPlanningMapper mrpPlanningMapper() { return new MrpPlanningMapper(); }

        @Bean(name = "mrpPlanningPermissionGuard")
        MrpPlanningPermissionGuard mrpPlanningPermissionGuard() { return mock(MrpPlanningPermissionGuard.class); }

        @Bean SupplySuggestionRepository supplySuggestionRepository() { return mock(SupplySuggestionRepository.class); }
        @Bean WorkOrderService workOrderService() { return mock(WorkOrderService.class); }
    }
}
