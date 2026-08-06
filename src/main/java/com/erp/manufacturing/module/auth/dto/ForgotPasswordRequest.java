package com.erp.manufacturing.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Forgot-password request. Always answered with the same generic response regardless of whether the
 * email is registered — account enumeration prevention (common/security/CLAUDE.md §4.13).
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email
) {
}
