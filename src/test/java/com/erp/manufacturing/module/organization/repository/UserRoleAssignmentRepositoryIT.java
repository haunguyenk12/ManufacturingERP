package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.organization.domain.AccessScope;
import com.erp.manufacturing.module.organization.domain.AccessScopeResource;
import com.erp.manufacturing.module.organization.domain.AssignmentStatus;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Permission;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.domain.RolePermission;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.ScopeType;
import com.erp.manufacturing.module.organization.domain.UserRoleAssignment;
import com.erp.manufacturing.module.organization.dto.ScopeResourcePermissionRow;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    private UUID setupSystemAdminAssignment(ScopeType scopeType, boolean attachResource) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();
        User user = User.builder()
                .username("admin-query-" + suffix)
                .email("admin-query-" + suffix + "@test.local")
                .password("encoded")
                .status(UserStatus.ACTIVE)
                .build();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = entityManager.persistFlushFind(user);

        Role admin = entityManager.getEntityManager()
                .createQuery("select r from Role r where r.code = 'ADMIN' and r.companyId is null", Role.class)
                .getSingleResult();
        AccessScope scope = AccessScope.builder()
                .code("ADMIN_SCOPE_" + suffix)
                .name("Admin scope " + suffix)
                .scopeType(scopeType)
                .status(OrganizationStatus.ACTIVE)
                .build();
        scope.setCreatedAt(now);
        scope.setUpdatedAt(now);
        scope = entityManager.persistFlushFind(scope);

        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .userId(user.getUserId())
                .roleId(admin.getRoleId())
                .scopeId(scope.getScopeId())
                .status(AssignmentStatus.ACTIVE)
                .build();
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        entityManager.persistAndFlush(assignment);
        if (attachResource) {
            attachResource(scope.getScopeId(), ScopeResourceType.PLANT, UUID.randomUUID());
        }
        entityManager.clear();
        return user.getUserId();
    }

    private boolean queryGlobalSystemAdmin(UUID userId) {
        return repository.existsActiveGlobalSystemAdminAssignment(
                userId, Instant.now(), AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE);
    }

    @Test
    void existsActiveGlobalSystemAdminAssignment_validGlobalAssignment_returnsTrue() {
        UUID userId = setupSystemAdminAssignment(ScopeType.GLOBAL, false);

        assertThat(queryGlobalSystemAdmin(userId)).isTrue();
    }

    @Test
    void existsActiveGlobalSystemAdminAssignment_scopedAssignment_returnsFalse() {
        UUID userId = setupSystemAdminAssignment(ScopeType.PLANT, true);

        assertThat(queryGlobalSystemAdmin(userId)).isFalse();
    }

    @Test
    void existsActiveGlobalSystemAdminAssignment_globalScopeWithResource_returnsFalse() {
        UUID userId = setupSystemAdminAssignment(ScopeType.GLOBAL, true);

        assertThat(queryGlobalSystemAdmin(userId)).isFalse();
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

    // ── findActiveScopeResourcePermissionRowsForUser (GET /api/auth/v1/me) ──────────────────────
    //
    // Same B32 shape as above (assignment/role/permission/scope status + expiresAt), plus the two
    // behaviours specific to this query: the LEFT JOIN on AccessScopeResource, and one row per
    // resource when a scope is attached to more than one.

    private record ScopeAssignmentData(UUID userId, String permissionCode, UUID scopeId) {
    }

    /** Same shape as {@link #setupAssignment}, but returns the scopeId so a case can attach
     *  zero, one, or several {@link AccessScopeResource} rows to it afterward. */
    private ScopeAssignmentData setupScopeAssignment(AssignmentStatus assignmentStatus, RoleStatus roleStatus,
            OrganizationStatus permissionStatus, OrganizationStatus scopeStatus, Instant expiresAt,
            ScopeType scopeType) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

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
                .scopeType(scopeType)
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

        return new ScopeAssignmentData(user.getUserId(), permissionCode, scope.getScopeId());
    }

    private void attachResource(UUID scopeId, ScopeResourceType resourceType, UUID resourceId) {
        AccessScopeResource resource = AccessScopeResource.builder()
                .scopeId(scopeId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .build();
        entityManager.persistAndFlush(resource);
    }

    private List<ScopeResourcePermissionRow> queryRows(UUID userId) {
        entityManager.clear();
        return repository.findActiveScopeResourcePermissionRowsForUser(
                userId, Instant.now(), AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE);
    }

    @Test
    void findActiveScopeResourcePermissionRowsForUser_excludesExpiredAssignment() {
        ScopeAssignmentData data = setupScopeAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE,
                Instant.now().minus(1, ChronoUnit.DAYS), ScopeType.GLOBAL);

        assertThat(queryRows(data.userId())).isEmpty();
    }

    @Test
    void findActiveScopeResourcePermissionRowsForUser_excludesInactiveAssignment() {
        ScopeAssignmentData data = setupScopeAssignment(AssignmentStatus.INACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE, null, ScopeType.GLOBAL);

        assertThat(queryRows(data.userId())).isEmpty();
    }

    @Test
    void findActiveScopeResourcePermissionRowsForUser_excludesInactiveRole() {
        ScopeAssignmentData data = setupScopeAssignment(AssignmentStatus.ACTIVE, RoleStatus.INACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE, null, ScopeType.GLOBAL);

        assertThat(queryRows(data.userId())).isEmpty();
    }

    @Test
    void findActiveScopeResourcePermissionRowsForUser_excludesInactivePermission() {
        ScopeAssignmentData data = setupScopeAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.INACTIVE, OrganizationStatus.ACTIVE, null, ScopeType.GLOBAL);

        assertThat(queryRows(data.userId())).isEmpty();
    }

    @Test
    void findActiveScopeResourcePermissionRowsForUser_excludesInactiveScope() {
        ScopeAssignmentData data = setupScopeAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.INACTIVE, null, ScopeType.GLOBAL);

        assertThat(queryRows(data.userId())).isEmpty();
    }

    /**
     * The query must use {@code left join AccessScopeResource}, not {@code inner join}: a GLOBAL
     * scope has no resource row at all, and an inner join would silently drop it from the result
     * instead of surfacing it with {@code resourceType/resourceId = null}.
     */
    @Test
    void findActiveScopeResourcePermissionRowsForUser_globalScopeWithoutResource_returnsRowWithNullResourceType() {
        ScopeAssignmentData data = setupScopeAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE, null, ScopeType.GLOBAL);
        // No AccessScopeResource attached — this scope grants everywhere.

        List<ScopeResourcePermissionRow> rows = queryRows(data.userId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).scopeType()).isEqualTo(ScopeType.GLOBAL);
        assertThat(rows.get(0).resourceType()).isNull();
        assertThat(rows.get(0).resourceId()).isNull();
        assertThat(rows.get(0).permissionCode()).isEqualTo(data.permissionCode());
    }

    @Test
    void findActiveScopeResourcePermissionRowsForUser_scopeWithMultipleResources_returnsOneRowPerResource() {
        ScopeAssignmentData data = setupScopeAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE, null, ScopeType.PLANT);
        UUID plantOne = UUID.randomUUID();
        UUID plantTwo = UUID.randomUUID();
        attachResource(data.scopeId(), ScopeResourceType.PLANT, plantOne);
        attachResource(data.scopeId(), ScopeResourceType.PLANT, plantTwo);

        List<ScopeResourcePermissionRow> rows = queryRows(data.userId());

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(ScopeResourcePermissionRow::resourceId)
                .containsExactlyInAnyOrder(plantOne, plantTwo);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.resourceType()).isEqualTo(ScopeResourceType.PLANT);
            assertThat(row.permissionCode()).isEqualTo(data.permissionCode());
        });
    }

    /**
     * Round-trips {@code expires_at} through Postgres on the exact query
     * {@code GET /access/assignments} runs — the integration test asked for in the frontend's
     * 2026-08-13 report, where a future expiry appeared to be lost.
     *
     * <p>It was not lost: the instant persisted fine and only the JSON serialisation was wrong
     * (epoch number instead of ISO, debt #27). This pins the half that report doubted, so the
     * question does not have to be re-litigated by hand: what goes into the column is what the
     * filtered read comes back with, to the second, across a {@code TIMESTAMPTZ} boundary.
     */
    @Test
    void search_returnsTheExpiresAtInstantThatWasPersisted() {
        Instant expiresAt = Instant.parse("2026-08-20T16:59:59Z");
        ScopeAssignmentData data = setupScopeAssignment(AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE, expiresAt, ScopeType.GLOBAL);

        Page<UserRoleAssignment> page = repository.search(
                data.userId(), null, data.scopeId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getExpiresAt()).isEqualTo(expiresAt);
    }
}
