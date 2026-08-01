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
) {
    /**
     * Masks {@code password}: the record-generated {@code toString()} prints every component, so any
     * log statement or exception message carrying this object would leak the credential
     * (see {@code best-practices.md} S14). The mask distinguishes absent from hidden — a missing
     * password and a hidden one are different bugs — but never reveals the length.
     */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username
                + ", password=" + (password == null ? "null" : "***")
                + ", deviceId=" + deviceId + "]";
    }
}
