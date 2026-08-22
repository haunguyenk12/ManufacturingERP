package com.erp.manufacturing.module.organization.security;

import com.erp.manufacturing.module.organization.domain.AssignmentStatus;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.organization.domain.ScopeType;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.UserRoleAssignmentRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.repository.UserRepository;
import com.erp.manufacturing.module.user.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security regression tests for the highest-risk RBAC escalation path.
 *
 * <p>These tests intentionally describe the required fail-closed behavior. Until the production
 * fix is implemented, the scoped ADMIN cases are expected to stay RED: a role obtained through a
 * scoped assignment must never become Spring's global {@code ROLE_ADMIN} authority.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Scoped ADMIN escalation security")
class ScopedAdminEscalationSecurityTest {

    @Mock UserRepository userRepository;
    @Mock UserRoleAssignmentRepository assignmentRepository;
    @Mock PlantRepository plantRepository;
    @Mock WarehouseRepository warehouseRepository;

    UserDetailsServiceImpl userDetailsService;
    PermissionGuard permissionGuard;

    @BeforeEach
    void setUp() {
        userDetailsService = new UserDetailsServiceImpl(userRepository, assignmentRepository);
        permissionGuard = new PermissionGuard(assignmentRepository, plantRepository, warehouseRepository);
    }

    @Test
    void dynamicAdminRoleFromScopedAssignment_neverBecomesGlobalAdminAuthority() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, Set.of());
        when(userRepository.findByUsernameWithRoles("scoped-admin")).thenReturn(Optional.of(user));
        stubDynamicAuthorities(userId, Set.of("ADMIN"), Set.of());

        UserDetails details = userDetailsService.loadUserByUsername("scoped-admin");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void prefixedDynamicAdminRole_neverBypassesReservedAuthorityFilter() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, Set.of());
        when(userRepository.findByUsernameWithRoles("prefixed-admin")).thenReturn(Optional.of(user));
        stubDynamicAuthorities(userId, Set.of("ROLE_ADMIN"), Set.of());

        UserDetails details = userDetailsService.loadUserByUsername("prefixed-admin");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void legacyRoleNameAlone_cannotForgeGlobalAdminAuthority() {
        Role forgedByName = Role.builder()
                .roleId(UUID.randomUUID())
                .code("LOCAL_OPERATOR")
                .name("ADMIN")
                .system(false)
                .status(RoleStatus.ACTIVE)
                .build();
        UUID userId = UUID.randomUUID();
        User user = user(userId, Set.of(forgedByName));
        when(userRepository.findByUsernameWithRoles("admin-by-name")).thenReturn(Optional.of(user));
        stubDynamicAuthorities(userId, Set.of(), Set.of());

        UserDetails details = userDetailsService.loadUserByUsername("admin-by-name");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void legacyGlobalSystemAdmin_keepsGlobalAdminAuthority() {
        Role globalSystemAdmin = Role.builder()
                .roleId(UUID.randomUUID())
                .companyId(null)
                .code("ADMIN")
                .name("ADMIN")
                .system(true)
                .status(RoleStatus.ACTIVE)
                .build();
        UUID userId = UUID.randomUUID();
        User user = user(userId, Set.of(globalSystemAdmin));
        when(userRepository.findByUsernameWithRoles("global-admin")).thenReturn(Optional.of(user));
        stubDynamicAuthorities(userId, Set.of(), Set.of());

        UserDetails details = userDetailsService.loadUserByUsername("global-admin");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN");
    }

    @Test
    void verifiedDynamicGlobalSystemAdmin_keepsGlobalAdminAuthority() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, Set.of());
        when(userRepository.findByUsernameWithRoles("dynamic-global-admin")).thenReturn(Optional.of(user));
        stubDynamicAuthorities(userId, Set.of("ADMIN"), Set.of());
        when(assignmentRepository.existsActiveGlobalSystemAdminAssignment(
                eq(userId), any(Instant.class), eq(AssignmentStatus.ACTIVE),
                eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(true);

        UserDetails details = userDetailsService.loadUserByUsername("dynamic-global-admin");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN");
    }

    @Test
    void permissionGuard_scopedAdminCodeCannotTriggerGlobalBypass() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(user(userId, Set.of()), Set.of("ADMIN"), Set.of());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(assignmentRepository.existsActivePermissionInScopeType(
                eq(userId), eq("PERM_ACCESS_MANAGE"), eq(ScopeType.GLOBAL), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE)))
                .thenReturn(false);

        assertThat(permissionGuard.hasPermission(authentication, "PERM_ACCESS_MANAGE")).isFalse();

        verify(assignmentRepository).existsActivePermissionInScopeType(
                eq(userId), eq("PERM_ACCESS_MANAGE"), eq(ScopeType.GLOBAL), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE));
    }

    private void stubDynamicAuthorities(UUID userId, Set<String> roles, Set<String> permissions) {
        when(assignmentRepository.findActiveRoleCodesForUser(
                eq(userId), any(Instant.class), eq(AssignmentStatus.ACTIVE),
                eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(roles);
        when(assignmentRepository.findActivePermissionCodesForUser(
                eq(userId), any(Instant.class), eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(permissions);
    }

    private User user(UUID userId, Set<Role> roles) {
        return User.builder()
                .userId(userId)
                .username("scoped-admin")
                .password("encoded")
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .build();
    }
}
