package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.organization.domain.AccessScope;
import com.erp.manufacturing.module.organization.domain.AssignmentStatus;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Permission;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.domain.RolePermission;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.organization.domain.ScopeType;
import com.erp.manufacturing.module.organization.domain.UserRoleAssignment;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real JPQL behind {@link UserRoleAssignmentRepository#existsActivePermissionInScopeType}
 * against a Postgres Testcontainer, instead of the fully-mocked repository used in
 * {@code PermissionGuardTest}. Closes invariant B32 (module/organization/CLAUDE.md):
 * expired/inactive assignment, role, permission, or scope must never grant access.
 *
 * <p>Each test changes exactly one dimension away from the happy-path (case 6) while
 * keeping the other four valid, to prove that dimension alone drives the JPQL result.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRoleAssignmentRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    UserRoleAssignmentRepository repository;

    @Autowired
    TestEntityManager entityManager;

    private record TestData(UUID userId, String permissionCode) {
    }

    private TestData setupAssignment(AssignmentStatus assignmentStatus, RoleStatus roleStatus,
            OrganizationStatus permissionStatus, OrganizationStatus scopeStatus, Instant expiresAt) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        // @DataJpaTest does not load the app's JpaAuditingConfig/SecurityAuditorAware, so
        // createdAt/updatedAt (NOT NULL columns on BaseEntity subclasses) are set explicitly
        // instead of relying on @CreatedDate/@LastModifiedDate auditing infrastructure.

        User user = User.builder()
                .username("user-" + suffix)
                .email(suffix + "@test.local")
                .password("encoded")
                .status(UserStatus.ACTIVE)
                .build();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = entityManager.persistFlushFind(user);

        Role role = entityManager.persistFlushFind(Role.builder()
                .code("ROLE_" + suffix)
                .name("Test Role " + suffix)
                .status(roleStatus)
                .system(false)
                .build());

        String permissionCode = "PERM_TEST_" + suffix;
        Permission permission = Permission.builder()
                .code(permissionCode)
                .resource("TEST_RESOURCE_" + suffix)
                .action("MANAGE")
                .status(permissionStatus)
                .build();
        permission.setCreatedAt(now);
        permission.setUpdatedAt(now);
        permission = entityManager.persistFlushFind(permission);

        entityManager.persistAndFlush(RolePermission.builder()
                .roleId(role.getRoleId())
                .permissionId(permission.getPermissionId())
                .build());

        AccessScope scope = AccessScope.builder()
                .code("SCOPE_" + suffix)
                .name("Test Scope " + suffix)
                .scopeType(ScopeType.GLOBAL)
                .status(scopeStatus)
                .build();
        scope.setCreatedAt(now);
        scope.setUpdatedAt(now);
        scope = entityManager.persistFlushFind(scope);

        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .userId(user.getUserId())
                .roleId(role.getRoleId())
                .scopeId(scope.getScopeId())
                .status(assignmentStatus)
                .expiresAt(expiresAt)
                .build();
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        entityManager.persistAndFlush(assignment);

        entityManager.clear();

        return new TestData(user.getUserId(), permissionCode);
    }

    private boolean queryExists(TestData data) {
        return repository.existsActivePermissionInScopeType(
                data.userId(), data.permissionCode(), ScopeType.GLOBAL, Instant.now(),
                AssignmentStatus.ACTIVE, RoleStatus.ACTIVE, OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE);
    }

    @Test
    void existsActivePermissionInScopeType_assignmentInactive_returnsFalse() {
        TestData data = setupAssignment(AssignmentStatus.INACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE, null);

        assertThat(queryExists(data)).isFalse();
    }

    @Test
    void existsActivePermissionInScopeType_roleInactive_returnsFalse() {
        TestData data = setupAssignment(AssignmentStatus.ACTIVE, RoleStatus.INACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE, null);

        assertThat(queryExists(data)).isFalse();
    }

    @Test
    void existsActivePermissionInScopeType_permissionInactive_returnsFalse() {
        TestData data = setupAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.INACTIVE, OrganizationStatus.ACTIVE, null);

        assertThat(queryExists(data)).isFalse();
    }

    @Test
    void existsActivePermissionInScopeType_scopeInactive_returnsFalse() {
        TestData data = setupAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.INACTIVE, null);

        assertThat(queryExists(data)).isFalse();
    }

    @Test
    void existsActivePermissionInScopeType_expiresAtInPast_returnsFalse() {
        TestData data = setupAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(queryExists(data)).isFalse();
    }

    @Test
    void existsActivePermissionInScopeType_allActiveAndNotExpired_returnsTrue() {
        TestData data = setupAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE, null);

        assertThat(queryExists(data)).isTrue();
    }
}
