package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.shift.dto.WorkCalendarCreateRequest;
import com.erp.manufacturing.module.shift.dto.WorkCalendarUpdateRequest;
import com.erp.manufacturing.module.shift.mapper.WorkCalendarMapper;
import com.erp.manufacturing.module.shift.repository.ShiftRepository;
import com.erp.manufacturing.module.shift.repository.WorkCalendarRepository;
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

@SpringJUnitConfig(WorkCalendarMethodSecurityTest.Config.class)
@DisplayName("WorkCalendarService method security")
class WorkCalendarMethodSecurityTest {

    @Autowired WorkCalendarService workCalendarService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired WorkCalendarPermissionGuard workCalendarPermissionGuard;
    @Autowired WorkCalendarRepository workCalendarRepository;
    @Autowired ShiftRepository shiftRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID CALENDAR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, workCalendarPermissionGuard, workCalendarRepository, shiftRepository, organizationLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_deniedWhenPlantManageScopeMissing() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_WORK_CALENDAR_MANAGE"), eq("PLANT"), eq(PLANT_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCalendarService.create(PLANT_ID, new WorkCalendarCreateRequest(
                "CAL-01", "Default Calendar", LocalDate.of(2026, 1, 1), null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCalendarRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_WORK_CALENDAR_MANAGE"), eq("PLANT"), eq(PLANT_ID));
    }

    @Test
    void create_allowedWhenPlantManageScopePresent() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_WORK_CALENDAR_MANAGE"), eq("PLANT"), eq(PLANT_ID))).thenReturn(true);
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(
                Plant.builder().plantId(PLANT_ID).code("PLANT").name("Plant")
                        .status(OrganizationStatus.ACTIVE).build());
        when(workCalendarRepository.existsByPlantPlantIdAndCode(PLANT_ID, "CAL-01")).thenReturn(false);
        when(workCalendarRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> workCalendarService.create(PLANT_ID, new WorkCalendarCreateRequest(
                "CAL-01", "Default Calendar", LocalDate.of(2026, 1, 1), null, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void list_deniedWhenPlantReadScopeMissing() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_WORK_CALENDAR_READ"), eq("PLANT"), eq(PLANT_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCalendarService.list(PLANT_ID, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCalendarRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_WORK_CALENDAR_READ"), eq("PLANT"), eq(PLANT_ID));
    }

    @Test
    void list_allowedWhenPlantReadScopePresent() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_WORK_CALENDAR_READ"), eq("PLANT"), eq(PLANT_ID))).thenReturn(true);
        when(workCalendarRepository.search(eq(PLANT_ID), any(), any())).thenReturn(Page.empty());

        assertThatCode(() -> workCalendarService.list(PLANT_ID, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void get_deniedWhenWorkCalendarReadScopeMissing() {
        when(workCalendarPermissionGuard.hasWorkCalendarAccess(
                any(), eq("PERM_WORK_CALENDAR_READ"), eq(CALENDAR_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCalendarService.get(CALENDAR_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCalendarRepository);
        verify(workCalendarPermissionGuard).hasWorkCalendarAccess(any(), eq("PERM_WORK_CALENDAR_READ"), eq(CALENDAR_ID));
    }

    @Test
    void update_deniedWhenWorkCalendarManageScopeMissing() {
        when(workCalendarPermissionGuard.hasWorkCalendarAccess(
                any(), eq("PERM_WORK_CALENDAR_MANAGE"), eq(CALENDAR_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCalendarService.update(CALENDAR_ID,
                new WorkCalendarUpdateRequest("New name", null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCalendarRepository);
        verify(workCalendarPermissionGuard).hasWorkCalendarAccess(any(), eq("PERM_WORK_CALENDAR_MANAGE"), eq(CALENDAR_ID));
    }

    @Test
    void activate_deniedWhenWorkCalendarManageScopeMissing() {
        when(workCalendarPermissionGuard.hasWorkCalendarAccess(
                any(), eq("PERM_WORK_CALENDAR_MANAGE"), eq(CALENDAR_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCalendarService.activate(CALENDAR_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCalendarRepository);
        verify(workCalendarPermissionGuard).hasWorkCalendarAccess(any(), eq("PERM_WORK_CALENDAR_MANAGE"), eq(CALENDAR_ID));
    }

    @Test
    void deactivate_deniedWhenWorkCalendarManageScopeMissing() {
        when(workCalendarPermissionGuard.hasWorkCalendarAccess(
                any(), eq("PERM_WORK_CALENDAR_MANAGE"), eq(CALENDAR_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCalendarService.deactivate(CALENDAR_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCalendarRepository);
        verify(workCalendarPermissionGuard).hasWorkCalendarAccess(any(), eq("PERM_WORK_CALENDAR_MANAGE"), eq(CALENDAR_ID));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        WorkCalendarService workCalendarService(WorkCalendarRepository workCalendarRepository,
                                                  ShiftRepository shiftRepository,
                                                  OrganizationLookupService organizationLookupService,
                                                  WorkCalendarMapper mapper) {
            return new WorkCalendarService(workCalendarRepository, shiftRepository, organizationLookupService, mapper);
        }

        @Bean WorkCalendarMapper workCalendarMapper() { return new WorkCalendarMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "workCalendarPermissionGuard")
        WorkCalendarPermissionGuard workCalendarPermissionGuard() { return mock(WorkCalendarPermissionGuard.class); }

        @Bean WorkCalendarRepository workCalendarRepository() { return mock(WorkCalendarRepository.class); }
        @Bean ShiftRepository shiftRepository() { return mock(ShiftRepository.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
    }
}
