package com.erp.manufacturing.module.auth.dto;

import com.erp.manufacturing.module.organization.dto.MyAccessScopeResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code GET /api/auth/v1/me} — self-service profile for the caller.
 *
 * <p>{@code roles}/{@code permissions} are the same flat union already carried in the access token's
 * {@code roles} claim (split by prefix), so a client that already decodes the JWT sees no surprises
 * here. {@code scopes}/{@code defaultPlantId} are the new information: permissions broken down by the
 * company/plant they actually apply to, which the JWT cannot carry.
 */
public record MeResponse(
        UUID userId,
        String username,
        String email,
        String status,
        Set<String> roles,
        Set<String> permissions,
        List<MyAccessScopeResponse> scopes,
        UUID defaultPlantId
) {}
