package com.erp.manufacturing.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Refresh token request.
 * {@code deviceId} must match the value sent at login to identify which device session to extend.
 */
public record RefreshRequest(
        @NotBlank(message = "Refresh token is required") String refreshToken,
        @NotBlank(message = "Token ID is required")      String tokenId,

        /** Must match the deviceId used at login. */
        @Size(max = 128, message = "deviceId must not exceed 128 characters")
        String deviceId
) {}
