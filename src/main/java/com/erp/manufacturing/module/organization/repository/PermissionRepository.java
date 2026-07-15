package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    boolean existsByCode(String code);

    boolean existsByResourceAndAction(String resource, String action);
}
