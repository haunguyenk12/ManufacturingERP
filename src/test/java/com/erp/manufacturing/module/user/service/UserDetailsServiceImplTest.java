package com.erp.manufacturing.module.user.service;

import com.erp.manufacturing.module.organization.domain.AssignmentStatus;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.repository.UserRoleAssignmentRepository;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl dynamic RBAC tests")
class UserDetailsServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock UserRoleAssignmentRepository assignmentRepository;

    UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsServiceImpl(userRepository, assignmentRepository);
    }

    @Test
    void loadUser_keepsLegacyRoleAndAddsDynamicAuthorities() {
        UUID userId = UUID.randomUUID();
        Role legacyAdmin = Role.builder()
                .roleId(UUID.randomUUID())
                .code("ADMIN")
                .name("ADMIN")
                .companyId(null)
                .system(true)
                .status(RoleStatus.ACTIVE)
                .build();
        User user = User.builder()
                .userId(userId)
                .username("admin")
                .password("encoded")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(legacyAdmin))
                .build();

        when(userRepository.findByUsernameWithRoles("admin")).thenReturn(Optional.of(user));
        when(assignmentRepository.findActiveRoleCodesForUser(
                eq(userId), any(Instant.class), eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(Set.of("PLANNER"));
        when(assignmentRepository.findActivePermissionCodesForUser(
                eq(userId), any(Instant.class), eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(Set.of("PERM_ORG_READ", "ORG_MANAGE"));

        UserDetails details = service.loadUserByUsername("admin");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_PLANNER", "PERM_ORG_READ", "PERM_ORG_MANAGE");
    }

    /**
     * NOTE (T5.2): this only proves {@code UserDetailsServiceImpl} passes the fixed-status
     * arguments through to the repository and produces zero authorities when the (mocked)
     * repository returns empty sets. The actual inactive/expired-assignment filtering logic
     * lives entirely inside the JPQL in {@code UserRoleAssignmentRepository} and is exercised
     * against a real database by {@code UserRoleAssignmentRepositoryIT} (T4.3), not here.
     */
    @Test
    void loadUser_returnsEmptyAuthoritiesWhenRepositoryReturnsNoActiveRolesOrPermissions() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .username("operator")
                .password("encoded")
                .status(UserStatus.ACTIVE)
                .roles(Set.of())
                .build();

        when(userRepository.findByUsernameWithRoles("operator")).thenReturn(Optional.of(user));
        when(assignmentRepository.findActiveRoleCodesForUser(
                eq(userId), any(Instant.class), eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(Set.of());
        when(assignmentRepository.findActivePermissionCodesForUser(
                eq(userId), any(Instant.class), eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(Set.of());

        UserDetails details = service.loadUserByUsername("operator");

        assertThat(details.getAuthorities()).isEmpty();
        verify(assignmentRepository).findActiveRoleCodesForUser(
                eq(userId), any(Instant.class), eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE));
    }
}
