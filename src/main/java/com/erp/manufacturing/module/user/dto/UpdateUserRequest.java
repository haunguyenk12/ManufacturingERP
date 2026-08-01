package com.erp.manufacturing.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for partial user update. All fields are optional.
 */
public record UpdateUserRequest(
        @Email(message = "Invalid email format")
        String email,

        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {
    /**
     * Masks {@code password}. Both fields are optional here, so the {@code null} case must stay
     * distinguishable: "no password sent" and "password hidden" lead to different investigations.
     */
    @Override
    public String toString() {
        return "UpdateUserRequest[email=" + email
                + ", password=" + (password == null ? "null" : "***") + "]";
    }
}
