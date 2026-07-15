package com.erp.manufacturing.module.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record UserRoleAssignmentResponse(
        UUID assignmentId,
        UUID userId,
        UUID roleId,
        UUID scopeId,
        String status,
        Instant expiresAt
) {}
