package com.erp.manufacturing.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request. {@code deviceId} is a stable client-generated identifier
 * (e.g. browser fingerprint, mobile device ID) used for multi-device session tracking.
 * If not provided, a default value is derived server-side from User-Agent + IP hash.
 */
public record LoginRequest(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required") String password,

        /** Stable device identifier from the client. Max 128 chars. */
        @Size(max = 128, message = "deviceId must not exceed 128 characters")
        String deviceId
) {}
