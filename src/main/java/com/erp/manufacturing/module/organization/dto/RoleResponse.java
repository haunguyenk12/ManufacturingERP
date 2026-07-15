package com.erp.manufacturing.module.organization.dto;

import java.util.UUID;

public record RoleResponse(
        UUID roleId,
        UUID companyId,
        String code,
        String name,
        String description,
        boolean system,
        String status
) {}
