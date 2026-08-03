package com.erp.manufacturing.module.organization.dto;

import java.util.Set;
import java.util.UUID;

/**
 * One entry of {@code GET /api/v1/auth/me}'s {@code scopes[]} — a company or plant the caller has
 * an active assignment on, with the permissions that apply to it.
 *
 * <p>{@code scopeType = "GLOBAL"} carries {@code companyId}/{@code plantId} both {@code null}: the
 * caller has the listed permissions everywhere, and there is no single resource to navigate to.
 * {@code scopeType = "COMPANY"} carries {@code plantId = null} — the permission applies to every
 * plant under that company (invariant B30), not to one plant in particular. This is a raw listing of
 * assigned scopes; it does not itself perform B30 inheritance resolution (that stays in
 * {@code PermissionGuard}, the single place that decides "is this permission effective on this
 * resource").
 */
public record MyAccessScopeResponse(
        String scopeType,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        Set<String> permissions
) {}
