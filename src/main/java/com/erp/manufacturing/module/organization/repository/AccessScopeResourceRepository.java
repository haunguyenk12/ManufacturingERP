package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.AccessScopeResource;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessScopeResourceRepository extends JpaRepository<AccessScopeResource, UUID> {

    boolean existsByScopeIdAndResourceTypeAndResourceId(UUID scopeId, ScopeResourceType resourceType, UUID resourceId);
}
