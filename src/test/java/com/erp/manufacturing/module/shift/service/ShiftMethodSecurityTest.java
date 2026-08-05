package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.shift.dto.ShiftCreateRequest;
import com.erp.manufacturing.module.shift.dto.ShiftUpdateRequest;
import com.erp.manufacturing.module.shift.mapper.ShiftMapper;
import com.erp.manufacturing.module.shift.repository.ShiftRepository;
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

import java.time.LocalTime;
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
 * plus at least one allow branch — same shape as {@code WorkCenterMethodSecurityTest}.
 */
@SpringJUnitConfig(ShiftMethodSecurityTest.Config.class)
@DisplayName("ShiftService method security")
class ShiftMethodSecurityTest {

    @Autowired ShiftService shiftService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired ShiftPermissionGuard shiftPermissionGuard;
    @Autowired ShiftRepository shiftRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID SHIFT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, shiftPermissionGuard, shiftRepository, organizationLookupService);
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
                any(), eq("PERM_SHIFT_MANAGE"), eq("PLANT"), eq(PLANT_ID))).thenReturn(false);

        assertThatThrownBy(() -> shiftService.create(PLANT_ID, new ShiftCreateRequest(
                "SH-01", "Day Shift", LocalTime.of(8, 0), LocalTime.of(17, 0), null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(shiftRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_SHIFT_MANAGE"), eq("PLANT"), eq(PLANT_ID));
    }

    @Test
    void create_allowedWhenPlantManageScopePresent() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_SHIFT_MANAGE"), eq("PLANT"), eq(PLANT_ID))).thenReturn(true);
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(
                Plant.builder().plantId(PLANT_ID).code("PLANT").name("Plant")
                        .status(OrganizationStatus.ACTIVE).build());
        when(shiftRepository.existsByPlantPlantIdAndCode(PLANT_ID, "SH-01")).thenReturn(false);
        when(shiftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> shiftService.create(PLANT_ID, new ShiftCreateRequest(
                "SH-01", "Day Shift", LocalTime.of(8, 0), LocalTime.of(17, 0), null)))
                .doesNotThrowAnyException();
    }

    @Test
    void list_deniedWhenPlantReadScopeMissing() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_SHIFT_READ"), eq("PLANT"), eq(PLANT_ID))).thenReturn(false);

        assertThatThrownBy(() -> shiftService.list(PLANT_ID, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(shiftRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_SHIFT_READ"), eq("PLANT"), eq(PLANT_ID));
    }

    @Test
    void list_allowedWhenPlantReadScopePresent() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_SHIFT_READ"), eq("PLANT"), eq(PLANT_ID))).thenReturn(true);
        when(shiftRepository.search(eq(PLANT_ID), any(), any())).thenReturn(Page.empty());

        assertThatCode(() -> shiftService.list(PLANT_ID, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void get_deniedWhenShiftReadScopeMissing() {
        when(shiftPermissionGuard.hasShiftAccess(any(), eq("PERM_SHIFT_READ"), eq(SHIFT_ID))).thenReturn(false);

        assertThatThrownBy(() -> shiftService.get(SHIFT_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(shiftRepository);
        verify(shiftPermissionGuard).hasShiftAccess(any(), eq("PERM_SHIFT_READ"), eq(SHIFT_ID));
    }

    @Test
    void update_deniedWhenShiftManageScopeMissing() {
        when(shiftPermissionGuard.hasShiftAccess(any(), eq("PERM_SHIFT_MANAGE"), eq(SHIFT_ID))).thenReturn(false);

        assertThatThrownBy(() -> shiftService.update(SHIFT_ID,
                new ShiftUpdateRequest("New name", null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(shiftRepository);
        verify(shiftPermissionGuard).hasShiftAccess(any(), eq("PERM_SHIFT_MANAGE"), eq(SHIFT_ID));
    }

    @Test
    void activate_deniedWhenShiftManageScopeMissing() {
        when(shiftPermissionGuard.hasShiftAccess(any(), eq("PERM_SHIFT_MANAGE"), eq(SHIFT_ID))).thenReturn(false);

        assertThatThrownBy(() -> shiftService.activate(SHIFT_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(shiftRepository);
        verify(shiftPermissionGuard).hasShiftAccess(any(), eq("PERM_SHIFT_MANAGE"), eq(SHIFT_ID));
    }

    @Test
    void deactivate_deniedWhenShiftManageScopeMissing() {
        when(shiftPermissionGuard.hasShiftAccess(any(), eq("PERM_SHIFT_MANAGE"), eq(SHIFT_ID))).thenReturn(false);

        assertThatThrownBy(() -> shiftService.deactivate(SHIFT_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(shiftRepository);
        verify(shiftPermissionGuard).hasShiftAccess(any(), eq("PERM_SHIFT_MANAGE"), eq(SHIFT_ID));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        ShiftService shiftService(ShiftRepository shiftRepository,
                                   OrganizationLookupService organizationLookupService,
                                   ShiftMapper mapper) {
            return new ShiftService(shiftRepository, organizationLookupService, mapper);
        }

        @Bean ShiftMapper shiftMapper() { return new ShiftMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "shiftPermissionGuard")
        ShiftPermissionGuard shiftPermissionGuard() { return mock(ShiftPermissionGuard.class); }

        @Bean ShiftRepository shiftRepository() { return mock(ShiftRepository.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
    }
}
