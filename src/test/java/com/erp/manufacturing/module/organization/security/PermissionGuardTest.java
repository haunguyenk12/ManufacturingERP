package com.erp.manufacturing.module.organization.security;

import com.erp.manufacturing.module.organization.domain.*;
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

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionGuard tests")
class PermissionGuardTest {

    @Mock UserRoleAssignmentRepository assignmentRepository;
    @Mock PlantRepository plantRepository;
    @Mock WarehouseRepository warehouseRepository;

    PermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PermissionGuard(assignmentRepository, plantRepository, warehouseRepository);
    }

    @Test
    void adminBypassesAllChecks() {
        Authentication auth = authWithLegacyRole("ADMIN");

        assertThat(guard.hasPermission(auth, "PERM_ACCESS_MANAGE")).isTrue();
        assertThat(guard.hasResourceAccess(auth, "PERM_ORG_MANAGE", "WAREHOUSE", UUID.randomUUID())).isTrue();
    }

    @Test
    void missingPermissionIsDenied() {
        UUID userId = UUID.randomUUID();
        Authentication auth = auth(userId);

        when(assignmentRepository.existsActivePermissionInScopeType(
                eq(userId), eq("PERM_ACCESS_MANAGE"), eq(ScopeType.GLOBAL), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(false);

        assertThat(guard.hasPermission(auth, "PERM_ACCESS_MANAGE")).isFalse();
    }

    @Test
    void companyScopeAllowsPlantAccess() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        Authentication auth = auth(userId);

        when(assignmentRepository.existsActiveResourcePermission(
                eq(userId), eq("PERM_ORG_READ"), eq(ScopeResourceType.PLANT), eq(plantId), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(false);
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant(plantId, companyId)));
        when(assignmentRepository.existsActiveResourcePermission(
                eq(userId), eq("PERM_ORG_READ"), eq(ScopeResourceType.COMPANY), eq(companyId), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(true);

        assertThat(guard.hasResourceAccess(auth, "PERM_ORG_READ", "PLANT", plantId)).isTrue();
    }

    @Test
    void plantScopeAllowsWarehouseAccess() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Authentication auth = auth(userId);

        when(assignmentRepository.existsActiveResourcePermission(
                eq(userId), eq("PERM_ORG_READ"), eq(ScopeResourceType.WAREHOUSE), eq(warehouseId), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(false);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, plantId, companyId)));
        when(assignmentRepository.existsActiveResourcePermission(
                eq(userId), eq("PERM_ORG_READ"), eq(ScopeResourceType.PLANT), eq(plantId), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(true);

        assertThat(guard.hasResourceAccess(auth, "PERM_ORG_READ", "WAREHOUSE", warehouseId)).isTrue();
    }

    @Test
    void warehouseScopeOnlyAllowsExactWarehouseWhenParentsDoNotMatch() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Authentication auth = auth(userId);

        when(assignmentRepository.existsActiveResourcePermission(
                eq(userId), eq("PERM_ORG_READ"), eq(ScopeResourceType.WAREHOUSE), eq(warehouseId), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(true);

        assertThat(guard.hasResourceAccess(auth, "PERM_ORG_READ", "WAREHOUSE", warehouseId)).isTrue();

        UUID otherWarehouseId = UUID.randomUUID();
        when(assignmentRepository.existsActiveResourcePermission(
                eq(userId), eq("PERM_ORG_READ"), eq(ScopeResourceType.WAREHOUSE), eq(otherWarehouseId), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(false);
        when(warehouseRepository.findById(otherWarehouseId)).thenReturn(Optional.of(warehouse(otherWarehouseId, plantId, companyId)));
        when(assignmentRepository.existsActiveResourcePermission(
                eq(userId), eq("PERM_ORG_READ"), eq(ScopeResourceType.PLANT), eq(plantId), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(false);
        when(assignmentRepository.existsActiveResourcePermission(
                eq(userId), eq("PERM_ORG_READ"), eq(ScopeResourceType.COMPANY), eq(companyId), any(Instant.class),
                eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE), eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(false);

        assertThat(guard.hasResourceAccess(auth, "PERM_ORG_READ", "WAREHOUSE", otherWarehouseId)).isFalse();
    }

    private Authentication authWithLegacyRole(String roleName) {
        Role role = Role.builder()
                .roleId(UUID.randomUUID())
                .name(roleName)
                .build();
        User user = user(UUID.randomUUID(), Set.of(role));
        UserPrincipal principal = new UserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Authentication auth(UUID userId) {
        UserPrincipal principal = new UserPrincipal(user(userId, Set.of()));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private User user(UUID userId, Set<Role> roles) {
        return User.builder()
                .userId(userId)
                .username("user")
                .password("encoded")
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .build();
    }

    private Plant plant(UUID plantId, UUID companyId) {
        return Plant.builder()
                .plantId(plantId)
                .company(Company.builder()
                        .companyId(companyId)
                        .code("ACME")
                        .name("ACME")
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, UUID plantId, UUID companyId) {
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant(plantId, companyId))
                .code("RM")
                .name("Raw material")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
