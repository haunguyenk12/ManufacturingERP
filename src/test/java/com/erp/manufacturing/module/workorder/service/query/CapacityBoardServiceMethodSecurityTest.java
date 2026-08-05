package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Rule R2: deny branch pairs with {@code verify(...)} pinning the exact permission string, plus an
 * allow branch — same shape as {@code WorkCenterMethodSecurityTest}.
 */
@SpringJUnitConfig(CapacityBoardServiceMethodSecurityTest.Config.class)
@DisplayName("CapacityBoardService method security")
class CapacityBoardServiceMethodSecurityTest {

    @Autowired CapacityBoardService capacityBoardService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired WorkOrderOperationRepository workOrderOperationRepository;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    @BeforeEach
    void setUp() {
        reset(permissionGuard, workOrderOperationRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getBoard_deniedWhenPlantReadScopeMissing() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_CAPACITY_READ"), eq("PLANT"), eq(PLANT_ID))).thenReturn(false);

        assertThatThrownBy(() -> capacityBoardService.getBoard(
                PLANT_ID, DAY, DAY, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderOperationRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_CAPACITY_READ"), eq("PLANT"), eq(PLANT_ID));
    }

    @Test
    void getBoard_allowedWhenPlantReadScopePresent() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_CAPACITY_READ"), eq("PLANT"), eq(PLANT_ID))).thenReturn(true);
        when(workOrderOperationRepository.searchCapacityBoard(eq(PLANT_ID), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        when(workOrderOperationRepository.aggregateExistingLoad(eq(PLANT_ID), any(), any(), any()))
                .thenReturn(List.of());

        assertThatCode(() -> capacityBoardService.getBoard(PLANT_ID, DAY, DAY, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        CapacityBoardService capacityBoardService(WorkOrderOperationRepository workOrderOperationRepository,
                                                   WorkCalendarLookupService workCalendarLookupService) {
            return new CapacityBoardService(workOrderOperationRepository, workCalendarLookupService);
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean WorkOrderOperationRepository workOrderOperationRepository() { return mock(WorkOrderOperationRepository.class); }
        @Bean WorkCalendarLookupService workCalendarLookupService() { return mock(WorkCalendarLookupService.class); }
    }
}
