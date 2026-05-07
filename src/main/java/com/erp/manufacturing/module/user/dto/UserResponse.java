package com.erp.manufacturing.module.user.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * User response DTO – never expose password field.
 */
public record UserResponse(
        UUID        userId,
        String      username,
        String      email,
        String      status,
        Set<String> roles,
        Instant     createdAt,
        Instant     updatedAt
) {}
