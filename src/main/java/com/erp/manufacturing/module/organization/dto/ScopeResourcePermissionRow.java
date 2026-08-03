package com.erp.manufacturing.module.organization.dto;

import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.ScopeType;

import java.util.UUID;

/**
 * One (scope, resource, permission) tuple produced by
 * {@link com.erp.manufacturing.module.organization.repository.UserRoleAssignmentRepository
 * #findActiveScopeResourcePermissionRowsForUser}.
 *
 * <p>{@code resourceType}/{@code resourceId} are {@code null} for a {@link ScopeType#GLOBAL} scope
 * (no {@code AccessScopeResource} row) — the query uses a {@code LEFT JOIN} on purpose so such scopes
 * still surface instead of disappearing.
 */
public record ScopeResourcePermissionRow(
        ScopeType scopeType,
        ScopeResourceType resourceType,
        UUID resourceId,
        String permissionCode
) {}
