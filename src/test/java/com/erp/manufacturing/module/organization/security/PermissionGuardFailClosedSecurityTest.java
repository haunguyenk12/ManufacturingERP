package com.erp.manufacturing.module.organization.security;

import com.erp.manufacturing.module.organization.domain.AssignmentStatus;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.organization.domain.ScopeType;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.UserRoleAssignmentRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.erp.manufacturing.module.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Post-remediation acceptance tests: malformed/untrusted inputs must always fail closed. */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionGuard fail-closed acceptance")
class PermissionGuardFailClosedSecurityTest {

    @Mock UserRoleAssignmentRepository assignmentRepository;
    @Mock PlantRepository plantRepository;
    @Mock WarehouseRepository warehouseRepository;

    PermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PermissionGuard(assignmentRepository, plantRepository, warehouseRepository);
    }

    @Test
    void nullAuthentication_isDeniedWithoutDatabaseAccess() {
        assertThat(guard.hasPermission(null, "PERM_ACCESS_MANAGE")).isFalse();
        verifyNoInteractions(assignmentRepository, plantRepository, warehouseRepository);
    }

    @Test
    void foreignPrincipalCannotGainAdminByInjectingRoleAdminString() {
        Authentication forged = new UsernamePasswordAuthenticationToken(
                "untrusted-principal", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(guard.hasPermission(forged, "PERM_ACCESS_MANAGE")).isFalse();

        verifyNoInteractions(assignmentRepository, plantRepository, warehouseRepository);
    }

    @Test
    void flatScopedPermissionAuthority_doesNotReplaceGlobalDatabaseDecision() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authentication(userId, Set.of("PERM_ACCESS_MANAGE"));
        stubGlobalPermission(userId, "PERM_ACCESS_MANAGE", false);

        assertThat(guard.hasPermission(auth, "PERM_ACCESS_MANAGE")).isFalse();

        verifyGlobalPermission(userId, "PERM_ACCESS_MANAGE");
    }

    @Test
    void nullPermissionCode_isDeniedInsteadOfThrowingOrQuerying() {
        Authentication auth = authentication(UUID.randomUUID(), Set.of());

        assertThatCode(() -> assertThat(guard.hasPermission(auth, null)).isFalse())
                .doesNotThrowAnyException();

        verifyNoInteractions(assignmentRepository, plantRepository, warehouseRepository);
    }

    @Test
    void blankPermissionCode_isDeniedWithoutResourceQuery() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authentication(userId, Set.of());

        assertThat(guard.hasPermission(auth, "   ")).isFalse();

        verifyNoInteractions(assignmentRepository, plantRepository, warehouseRepository);
    }

    @Test
    void nullResourceId_isDeniedBeforeAnyDatabaseLookup() {
        Authentication auth = authentication(UUID.randomUUID(), Set.of());

        assertThat(guard.hasResourceAccess(auth, "PERM_ORG_READ", "PLANT", null)).isFalse();

        verifyNoInteractions(assignmentRepository, plantRepository, warehouseRepository);
    }

    @Test
    void unsupportedResourceType_isDeniedWithoutDirectResourceLookup() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authentication(userId, Set.of());

        assertThat(guard.hasResourceAccess(
                auth, "PERM_ORG_READ", "TENANT_ROOT", UUID.randomUUID())).isFalse();

        verify(assignmentRepository, never()).existsActiveResourcePermission(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(plantRepository, warehouseRepository);
    }

    @Test
    void revokedPermission_isObservedOnTheVeryNextRequest() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authentication(userId, Set.of());
        when(assignmentRepository.existsActivePermissionInScopeType(
                eq(userId), eq("PERM_AUDIT_READ"), eq(ScopeType.GLOBAL), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE)))
                .thenReturn(true, false);

        assertThat(guard.hasPermission(auth, "PERM_AUDIT_READ")).isTrue();
        assertThat(guard.hasPermission(auth, "PERM_AUDIT_READ")).isFalse();
    }

    private Authentication authentication(UUID userId, Set<String> permissionCodes) {
        User user = User.builder()
                .userId(userId)
                .username("operator")
                .password("encoded")
                .status(UserStatus.ACTIVE)
                .roles(Set.of())
                .build();
        UserPrincipal principal = new UserPrincipal(user, Set.of(), permissionCodes);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void stubGlobalPermission(UUID userId, String permission, boolean result) {
        when(assignmentRepository.existsActivePermissionInScopeType(
                eq(userId), eq(permission), eq(ScopeType.GLOBAL), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE)))
                .thenReturn(result);
    }

    private void verifyGlobalPermission(UUID userId, String permission) {
        verify(assignmentRepository).existsActivePermissionInScopeType(
                eq(userId), eq(permission), eq(ScopeType.GLOBAL), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE));
    }
}
