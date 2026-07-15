package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.RolePermission;
import com.erp.manufacturing.module.organization.domain.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    void deleteByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
}
