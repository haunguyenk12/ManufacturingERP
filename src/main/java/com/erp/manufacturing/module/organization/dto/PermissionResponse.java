package com.erp.manufacturing.module.organization.dto;

import java.util.UUID;

public record PermissionResponse(
        UUID permissionId,
        String code,
        String resource,
        String action,
        String description,
        String status
) {}
