package com.erp.manufacturing.module.organization.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code code}/{@code isSystem} are deliberately absent — identity/lifecycle fields, not something
 * PATCH replaces (same "null = unchanged" convention as {@code SupplierUpdateRequest}).
 */
public record RoleUpdateRequest(
        @Size(max = 100) String name,
        String description
) {}
