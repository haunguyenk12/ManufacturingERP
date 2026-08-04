package com.erp.manufacturing.module.organization.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code code}/{@code scopeType} are deliberately absent — identity fields, not something PATCH
 * replaces (same "null = unchanged" convention as {@code SupplierUpdateRequest}).
 */
public record AccessScopeUpdateRequest(
        @Size(max = 255) String name,
        String description
) {}
