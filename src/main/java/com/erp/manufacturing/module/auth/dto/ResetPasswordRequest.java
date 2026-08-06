package com.erp.manufacturing.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reset-password request.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Token is required") String token,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword
) {
    /**
     * Masks both {@code token} and {@code newPassword}. Unlike {@code RefreshRequest.tokenId} (a
     * lookup key next to a separately-compared secret), {@code token} here IS the credential —
     * knowing it alone lets anyone reset that account's password.
     */
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=" + (token == null ? "null" : "***")
                + ", newPassword=" + (newPassword == null ? "null" : "***") + "]";
    }
}
