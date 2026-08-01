package com.erp.manufacturing.module.organization.service;

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

    @BeforeEach
    void setUp() {
        reset(permissionGuard, roleRepository, permissionRepository);
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
