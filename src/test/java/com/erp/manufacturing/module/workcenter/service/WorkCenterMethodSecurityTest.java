package com.erp.manufacturing.module.workcenter.service;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterCreateRequest;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterUpdateRequest;
import com.erp.manufacturing.module.workcenter.mapper.WorkCenterMapper;
import com.erp.manufacturing.module.workcenter.repository.WorkCenterRepository;
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
 * Rule R2: every deny branch pairs with {@code verify(...)} pinning the exact permission string,
 * plus at least one allow branch. {@code create}/{@code list} authorize against the {@code plantId}
 * path segment directly (@permissionGuard); the aggregate-identified methods resolve plant through
 * {@code workCenterPermissionGuard}, same shape as {@code RoutingMethodSecurityTest}.
 */
@SpringJUnitConfig(WorkCenterMethodSecurityTest.Config.class)
@DisplayName("WorkCenterService method security")
class WorkCenterMethodSecurityTest {

    @Autowired WorkCenterService workCenterService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired WorkCenterPermissionGuard workCenterPermissionGuard;
    @Autowired WorkCenterRepository workCenterRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID WORK_CENTER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, workCenterPermissionGuard, workCenterRepository, organizationLookupService);
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
                any(), eq("PERM_WORK_CENTER_MANAGE"), eq("PLANT"), eq(PLANT_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCenterService.create(PLANT_ID, new WorkCenterCreateRequest(
                "WC-01", "Line 1", null, CapacityUnitType.LINE, 1)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCenterRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_WORK_CENTER_MANAGE"), eq("PLANT"), eq(PLANT_ID));
    }

    @Test
    void create_allowedWhenPlantManageScopePresent() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_WORK_CENTER_MANAGE"), eq("PLANT"), eq(PLANT_ID))).thenReturn(true);
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(
                Plant.builder().plantId(PLANT_ID).code("PLANT").name("Plant")
                        .status(OrganizationStatus.ACTIVE).build());
        when(workCenterRepository.existsByPlantPlantIdAndCode(PLANT_ID, "WC-01")).thenReturn(false);
        when(workCenterRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> workCenterService.create(PLANT_ID, new WorkCenterCreateRequest(
                "WC-01", "Line 1", null, CapacityUnitType.LINE, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void list_deniedWhenPlantReadScopeMissing() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_WORK_CENTER_READ"), eq("PLANT"), eq(PLANT_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCenterService.list(PLANT_ID, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCenterRepository);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_WORK_CENTER_READ"), eq("PLANT"), eq(PLANT_ID));
    }

    @Test
    void list_allowedWhenPlantReadScopePresent() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_WORK_CENTER_READ"), eq("PLANT"), eq(PLANT_ID))).thenReturn(true);
        when(workCenterRepository.search(eq(PLANT_ID), any(), any())).thenReturn(Page.empty());

        assertThatCode(() -> workCenterService.list(PLANT_ID, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void get_deniedWhenWorkCenterReadScopeMissing() {
        when(workCenterPermissionGuard.hasWorkCenterAccess(
                any(), eq("PERM_WORK_CENTER_READ"), eq(WORK_CENTER_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCenterService.get(WORK_CENTER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCenterRepository);
        verify(workCenterPermissionGuard).hasWorkCenterAccess(any(), eq("PERM_WORK_CENTER_READ"), eq(WORK_CENTER_ID));
    }

    @Test
    void update_deniedWhenWorkCenterManageScopeMissing() {
        when(workCenterPermissionGuard.hasWorkCenterAccess(
                any(), eq("PERM_WORK_CENTER_MANAGE"), eq(WORK_CENTER_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCenterService.update(WORK_CENTER_ID,
                new WorkCenterUpdateRequest("New name", null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCenterRepository);
        verify(workCenterPermissionGuard).hasWorkCenterAccess(any(), eq("PERM_WORK_CENTER_MANAGE"), eq(WORK_CENTER_ID));
    }

    @Test
    void activate_deniedWhenWorkCenterManageScopeMissing() {
        when(workCenterPermissionGuard.hasWorkCenterAccess(
                any(), eq("PERM_WORK_CENTER_MANAGE"), eq(WORK_CENTER_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCenterService.activate(WORK_CENTER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCenterRepository);
        verify(workCenterPermissionGuard).hasWorkCenterAccess(any(), eq("PERM_WORK_CENTER_MANAGE"), eq(WORK_CENTER_ID));
    }

    @Test
    void deactivate_deniedWhenWorkCenterManageScopeMissing() {
        when(workCenterPermissionGuard.hasWorkCenterAccess(
                any(), eq("PERM_WORK_CENTER_MANAGE"), eq(WORK_CENTER_ID))).thenReturn(false);

        assertThatThrownBy(() -> workCenterService.deactivate(WORK_CENTER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workCenterRepository);
        verify(workCenterPermissionGuard).hasWorkCenterAccess(any(), eq("PERM_WORK_CENTER_MANAGE"), eq(WORK_CENTER_ID));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        WorkCenterService workCenterService(WorkCenterRepository workCenterRepository,
                                            OrganizationLookupService organizationLookupService,
                                            WorkCenterMapper mapper) {
            return new WorkCenterService(workCenterRepository, organizationLookupService, mapper);
        }

        @Bean WorkCenterMapper workCenterMapper() { return new WorkCenterMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "workCenterPermissionGuard")
        WorkCenterPermissionGuard workCenterPermissionGuard() { return mock(WorkCenterPermissionGuard.class); }

        @Bean WorkCenterRepository workCenterRepository() { return mock(WorkCenterRepository.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
    }
}
