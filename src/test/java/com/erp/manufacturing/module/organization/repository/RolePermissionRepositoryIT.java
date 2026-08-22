package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Permission;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.domain.RolePermission;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real JPQL behind {@link RolePermissionRepository#findPermissionsByRoleId} against a
 * Postgres Testcontainer.
 *
 * <p>Required rather than optional (rule R7): the whole meaning of the query — the correlated
 * subquery over the {@code role_permissions} link table, and the fact that it does not filter on
 * permission status — lives inside the JPQL string. A mocked repository would return whatever the
 * stub was told to return and prove nothing, which is exactly how a wrong membership read would
 * reach the admin UI unnoticed and silently render a granted permission as unchecked.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RolePermissionRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    RolePermissionRepository repository;

    @Autowired
    TestEntityManager entityManager;

    // @DataJpaTest does not load the app's JpaAuditingConfig, so createdAt/updatedAt (NOT NULL on
    // BaseEntity subclasses) are set explicitly rather than via @CreatedDate/@LastModifiedDate.
    private Role persistRole() {
        return entityManager.persistFlushFind(Role.builder()
                .code("ROLE_" + UUID.randomUUID())
                .name("Test Role")
                .status(RoleStatus.ACTIVE)
                .system(false)
                .build());
    }

    private Permission persistPermission(String codeSuffix, OrganizationStatus status) {
        Instant now = Instant.now();
        Permission permission = Permission.builder()
                .code("PERM_" + codeSuffix)
                .resource("RES_" + codeSuffix)
                .action("READ")
                .status(status)
                .build();
        permission.setCreatedAt(now);
        permission.setUpdatedAt(now);
        return entityManager.persistFlushFind(permission);
    }

    private void grant(UUID roleId, UUID permissionId) {
        entityManager.persistAndFlush(RolePermission.builder()
                .roleId(roleId)
                .permissionId(permissionId)
                .build());
    }

    private Page<Permission> query(UUID roleId) {
        return repository.findPermissionsByRoleId(roleId, PageRequest.of(0, 20, Sort.by("code").ascending()));
    }

    @Test
    void findPermissionsByRoleId_returnsOnlyThePermissionsGrantedToThatRole() {
        Role role = persistRole();
        Role otherRole = persistRole();
        Permission granted = persistPermission("GRANTED_" + UUID.randomUUID(), OrganizationStatus.ACTIVE);
        Permission notGranted = persistPermission("UNGRANTED_" + UUID.randomUUID(), OrganizationStatus.ACTIVE);
        Permission grantedElsewhere = persistPermission("OTHER_" + UUID.randomUUID(), OrganizationStatus.ACTIVE);

        grant(role.getRoleId(), granted.getPermissionId());
        grant(otherRole.getRoleId(), grantedElsewhere.getPermissionId());
        entityManager.clear();

        assertThat(query(role.getRoleId()).getContent())
                .extracting(Permission::getPermissionId)
                .containsExactly(granted.getPermissionId())
                .doesNotContain(notGranted.getPermissionId(), grantedElsewhere.getPermissionId());
    }

    @Test
    void findPermissionsByRoleId_roleWithoutAnyGrant_returnsEmptyPage() {
        Role role = persistRole();
        persistPermission("CATALOGUE_ONLY_" + UUID.randomUUID(), OrganizationStatus.ACTIVE);
        entityManager.clear();

        Page<Permission> result = query(role.getRoleId());

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    /**
     * A permission that was granted and later deactivated is still <em>held</em> by the role. Hiding
     * it would show the admin an unchecked box for a grant that exists in the database, and the next
     * save would drop it without anyone intending to.
     */
    @Test
    void findPermissionsByRoleId_includesGrantedPermissionsThatAreNoLongerActive() {
        Role role = persistRole();
        Permission inactive = persistPermission("INACTIVE_" + UUID.randomUUID(), OrganizationStatus.INACTIVE);
        grant(role.getRoleId(), inactive.getPermissionId());
        entityManager.clear();

        assertThat(query(role.getRoleId()).getContent())
                .extracting(Permission::getPermissionId)
                .containsExactly(inactive.getPermissionId());
    }

    @Test
    void findPermissionsByRoleId_paginatesAndCountsTheFullMembership() {
        Role role = persistRole();
        for (int i = 0; i < 3; i++) {
            Permission permission = persistPermission("PAGED_" + i + "_" + UUID.randomUUID(), OrganizationStatus.ACTIVE);
            grant(role.getRoleId(), permission.getPermissionId());
        }
        entityManager.clear();

        Page<Permission> firstPage = repository.findPermissionsByRoleId(
                role.getRoleId(), PageRequest.of(0, 2, Sort.by("code").ascending()));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.isLast()).isFalse();
    }
}
