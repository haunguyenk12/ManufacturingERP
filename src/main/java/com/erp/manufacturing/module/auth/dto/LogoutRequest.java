package com.erp.manufacturing.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh token is required") String refreshToken,
        @NotBlank(message = "Token ID is required")     String tokenId
) {
    /** Masks {@code refreshToken}; {@code tokenId} is an identifier, not a credential. */
    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=" + (refreshToken == null ? "null" : "***")
                + ", tokenId=" + tokenId + "]";
    }
}
