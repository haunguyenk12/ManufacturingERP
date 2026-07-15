package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.AccessScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessScopeRepository extends JpaRepository<AccessScope, UUID> {

    boolean existsByCode(String code);
}
