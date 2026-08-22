package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.AccessScopeResource;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessScopeResourceRepository extends JpaRepository<AccessScopeResource, UUID> {

    boolean existsByScopeIdAndResourceTypeAndResourceId(UUID scopeId, ScopeResourceType resourceType, UUID resourceId);

    /** Resource membership of one scope — the read side of {@code POST /access/scopes/{id}/resources}. */
    Page<AccessScopeResource> findByScopeId(UUID scopeId, Pageable pageable);
}
