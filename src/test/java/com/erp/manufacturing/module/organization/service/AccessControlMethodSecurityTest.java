package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.module.organization.domain.AccessScope;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.organization.domain.ScopeType;
import com.erp.manufacturing.module.organization.dto.AccessScopeUpdateRequest;
import com.erp.manufacturing.module.organization.dto.RoleUpdateRequest;
import com.erp.manufacturing.module.organization.mapper.OrganizationMapper;
import com.erp.manufacturing.module.organization.repository.*;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.user.repository.UserRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(AccessControlMethodSecurityTest.Config.class)
@DisplayName("AccessControlService method security")
class AccessControlMethodSecurityTest {

    @Autowired AccessControlService accessControlService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired RoleRepository roleRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired AccessScopeRepository accessScopeRepository;
    @Autowired UserRoleAssignmentRepository assignmentRepository;
    @Autowired RolePermissionRepository rolePermissionRepository;
    @Autowired AccessScopeResourceRepository accessScopeResourceRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, roleRepository, permissionRepository, accessScopeRepository, assignmentRepository,
                rolePermissionRepository, accessScopeResourceRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listRoles_deniedWhenAccessManageMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.listRoles(PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(roleRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_ACCESS_MANAGE"));
    }

    @Test
    void listPermissions_deniedWhenAccessManageMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.listPermissions(PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(permissionRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_ACCESS_MANAGE"));
    }

    @Test
    void listRoles_allowedWhenAccessManagePresent() {
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(true);
        when(roleRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        assertThatCode(() -> accessControlService.listRoles(PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    // ── role/scope lifecycle + assignments (C2-4) ──────────────────────────

    @Test
    void getRole_deniedWhenAccessManageMissing() {
        UUID roleId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.getRole(roleId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(roleRepository);
    }

    @Test
    void updateRole_deniedWhenAccessManageMissing() {
        UUID roleId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.updateRole(roleId, new RoleUpdateRequest("New", null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(roleRepository);
    }

    @Test
    void activateRole_deniedWhenAccessManageMissing() {
        UUID roleId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.activateRole(roleId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(roleRepository);
    }

    @Test
    void deactivateRole_deniedWhenAccessManageMissing() {
        UUID roleId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.deactivateRole(roleId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(roleRepository);
    }

    @Test
    void deactivateRole_allowedWhenAccessManagePresent() {
        UUID roleId = UUID.randomUUID();
        Role role = Role.builder().roleId(roleId).code("PLANNER").name("Planner")
                .system(false).status(RoleStatus.ACTIVE).build();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(true);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> accessControlService.deactivateRole(roleId)).doesNotThrowAnyException();
    }

    @Test
    void getScope_deniedWhenAccessManageMissing() {
        UUID scopeId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.getScope(scopeId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(accessScopeRepository);
    }

    @Test
    void updateScope_deniedWhenAccessManageMissing() {
        UUID scopeId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.updateScope(scopeId, new AccessScopeUpdateRequest("New", null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(accessScopeRepository);
    }

    @Test
    void activateScope_deniedWhenAccessManageMissing() {
        UUID scopeId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.activateScope(scopeId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(accessScopeRepository);
    }

    @Test
    void deactivateScope_deniedWhenAccessManageMissing() {
        UUID scopeId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.deactivateScope(scopeId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(accessScopeRepository);
    }

    @Test
    void deactivateScope_allowedWhenAccessManagePresent() {
        UUID scopeId = UUID.randomUUID();
        AccessScope scope = AccessScope.builder().scopeId(scopeId).code("SCOPE").name("Scope")
                .scopeType(ScopeType.CUSTOM).status(OrganizationStatus.ACTIVE).build();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(true);
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(scope));
        when(accessScopeRepository.save(any(AccessScope.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> accessControlService.deactivateScope(scopeId)).doesNotThrowAnyException();
    }

    @Test
    void listAssignments_deniedWhenAccessManageMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.listAssignments(null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void listAssignments_allowedWhenAccessManagePresent() {
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(true);
        when(assignmentRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        assertThatCode(() -> accessControlService.listAssignments(null, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    // ── membership reads (FE RBAC contract) ────────────────────────────────

    @Test
    void listRolePermissions_deniedWhenAccessManageMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.listRolePermissions(UUID.randomUUID(), PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(roleRepository, rolePermissionRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_ACCESS_MANAGE"));
    }

    @Test
    void listRolePermissions_allowedWhenAccessManagePresent() {
        UUID roleId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(true);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(Role.builder()
                .roleId(roleId).code("PLANNER").name("Planner").status(RoleStatus.ACTIVE).build()));
        when(rolePermissionRepository.findPermissionsByRoleId(eq(roleId), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> accessControlService.listRolePermissions(roleId, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void listScopeResources_deniedWhenAccessManageMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.listScopeResources(UUID.randomUUID(), PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(accessScopeRepository, accessScopeResourceRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_ACCESS_MANAGE"));
    }

    @Test
    void listScopeResources_allowedWhenAccessManagePresent() {
        UUID scopeId = UUID.randomUUID();
        when(permissionGuard.hasPermission(any(), eq("PERM_ACCESS_MANAGE"))).thenReturn(true);
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(AccessScope.builder()
                .scopeId(scopeId).code("SCOPE").name("Scope")
                .scopeType(ScopeType.CUSTOM).status(OrganizationStatus.ACTIVE).build()));
        when(accessScopeResourceRepository.findByScopeId(eq(scopeId), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> accessControlService.listScopeResources(scopeId, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AccessControlService accessControlService(RoleRepository roleRepository,
                                                  UserRepository userRepository,
                                                  CompanyRepository companyRepository,
                                                  PlantRepository plantRepository,
                                                  WarehouseRepository warehouseRepository,
                                                  PermissionRepository permissionRepository,
                                                  RolePermissionRepository rolePermissionRepository,
                                                  AccessScopeRepository accessScopeRepository,
                                                  AccessScopeResourceRepository accessScopeResourceRepository,
                                                  UserRoleAssignmentRepository assignmentRepository,
                                                  OrganizationMapper mapper) {
            return new AccessControlService(
                    roleRepository, userRepository, companyRepository, plantRepository, warehouseRepository,
                    permissionRepository, rolePermissionRepository, accessScopeRepository,
                    accessScopeResourceRepository, assignmentRepository, mapper);
        }

        @Bean OrganizationMapper organizationMapper() { return new OrganizationMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean RoleRepository roleRepository() { return mock(RoleRepository.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean CompanyRepository companyRepository() { return mock(CompanyRepository.class); }
        @Bean PlantRepository plantRepository() { return mock(PlantRepository.class); }
        @Bean WarehouseRepository warehouseRepository() { return mock(WarehouseRepository.class); }
        @Bean PermissionRepository permissionRepository() { return mock(PermissionRepository.class); }
        @Bean RolePermissionRepository rolePermissionRepository() { return mock(RolePermissionRepository.class); }
        @Bean AccessScopeRepository accessScopeRepository() { return mock(AccessScopeRepository.class); }
        @Bean AccessScopeResourceRepository accessScopeResourceRepository() { return mock(AccessScopeResourceRepository.class); }
        @Bean UserRoleAssignmentRepository userRoleAssignmentRepository() { return mock(UserRoleAssignmentRepository.class); }
    }
}
