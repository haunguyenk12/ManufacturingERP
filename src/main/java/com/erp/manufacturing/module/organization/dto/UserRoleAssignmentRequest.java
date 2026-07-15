package com.erp.manufacturing.module.organization.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record UserRoleAssignmentRequest(
        @NotNull
        UUID userId,

        @NotNull
        UUID roleId,

        @NotNull
        UUID scopeId,

        Instant expiresAt
) {}
