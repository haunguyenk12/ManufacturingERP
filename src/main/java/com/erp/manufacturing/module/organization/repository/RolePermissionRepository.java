package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.Permission;
import com.erp.manufacturing.module.organization.domain.RolePermission;
import com.erp.manufacturing.module.organization.domain.RolePermissionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    void deleteByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    /**
     * The permissions currently granted to one role — the authoritative membership behind
     * {@code GET /api/v1/access/roles/{roleId}/permissions}.
     *
     * <p>Expressed as a subquery rather than a join because {@link RolePermission} is a bare
     * {@code @IdClass} link table with no association mapping to {@link Permission}; adding one only
     * to serve this read would change the entity graph of every existing grant/revoke path.
     *
     * <p>Deliberately returns permissions of <b>every</b> status, not just {@code ACTIVE}: this is a
     * membership read, and a role that holds a since-deactivated permission must still show that row
     * in the admin UI — hiding it would render a checkbox as unchecked and let the next save silently
     * drop a grant that is actually stored.
     */
    @Query("select p from Permission p where p.permissionId in "
            + "(select rp.permissionId from RolePermission rp where rp.roleId = :roleId)")
    Page<Permission> findPermissionsByRoleId(@Param("roleId") UUID roleId, Pageable pageable);
}
